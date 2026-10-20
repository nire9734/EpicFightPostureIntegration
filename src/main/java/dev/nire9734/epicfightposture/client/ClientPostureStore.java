package dev.nire9734.epicfightposture.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ClientPostureStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ConcurrentMap<Integer, Entry> STATES = new ConcurrentHashMap<>();
    /** Log first break of each entity type, not every subsequent packet/frame. */
    private static final Set<ResourceLocation> DIAGNOSED_TYPES = ConcurrentHashMap.newKeySet();

    public static void update(int entityId, UUID uuid, float current, float max, boolean broken) {
        if (uuid == null || max <= 0.0F) {
            STATES.remove(entityId);
            return;
        }
        Entry previous = STATES.put(entityId, new Entry(uuid, current, max, broken));
        if (broken && (previous == null || !previous.broken)) {
            logFirstBreakRendererState(entityId);
        }
    }

    public static void remove(int entityId, UUID uuid) {
        Entry entry = STATES.get(entityId);
        if (entry != null && (uuid == null || uuid.equals(entry.uuid))) {
            STATES.remove(entityId, entry);
        }
    }

    public static Entry get(int entityId, UUID uuid) {
        Entry entry = STATES.get(entityId);
        return entry != null && uuid != null && uuid.equals(entry.uuid) ? entry : null;
    }

    public static void clear() {
        STATES.clear();
        DIAGNOSED_TYPES.clear();
    }

    /**
     * Observes the client only after receiving the server's first broken state.
     * No mutations to the target's patch, animator, renderer or compatibility.
     */
    private static void logFirstBreakRendererState(int entityId) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            var entity = minecraft.level.getEntity(entityId);
            if (!(entity instanceof LivingEntity living)) {
                return;
            }
            ResourceLocation id = EntityType.getKey(living.getType());
            if (id == null || !DIAGNOSED_TYPES.add(id)) {
                return;
            }
            LivingEntityPatch<?> patch =
                    EpicFightCapabilities.getEntityPatch(living, LivingEntityPatch.class);
            boolean overrides = patch != null && patch.overrideRender();
            String patchName = patch == null ? "none" : patch.getClass().getName();
            String sourceRenderer = minecraft.getEntityRenderDispatcher()
                    .getRenderer(living).getClass().getName();
            LOGGER.info("EFPI first guard-break render check: mob={}, patch={}, overrideRender={}, autoCompatEnabled={}, sourceRenderer={}",
                    id, patchName, overrides,
                    VanillaHumanoidCompat.isCompatibilityEnabled(living.getType()), sourceRenderer);
        } catch (Throwable failure) {
            LOGGER.warn("EFPI client guard-break render diagnosis failed for entity {}", entityId, failure);
        }
    }

    private ClientPostureStore() {}

    public static final class Entry {
        public final UUID uuid;
        public final float current;
        public final float max;
        public final boolean broken;

        Entry(UUID uuid, float current, float max, boolean broken) {
            this.uuid = uuid;
            this.current = current;
            this.max = max;
            this.broken = broken;
        }

        public float percent() {
            if (max <= 0.0F) return 0.0F;
            return Math.max(0.0F, Math.min(1.0F, current / max));
        }
    }
}
