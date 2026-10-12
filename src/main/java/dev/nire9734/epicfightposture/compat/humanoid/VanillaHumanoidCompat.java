package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.util.HashSet;
import java.util.Set;

/**
 * Lightweight EF patch registration for third-party HumanoidModel entities.
 *
 * <p>Physical clients discover HumanoidModel renderers automatically. The
 * discovery result is cached for the next startup and can also be supplemented
 * with force_include for dedicated servers. Existing Epic Fight patches always
 * win, and compatibility-blacklisted IDs are never registered here.</p>
 */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class VanillaHumanoidCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Set<ResourceLocation> cachedDetected = Set.of();
    private static final Set<ResourceLocation> runtimeDetected = new HashSet<>();

    public static void bootstrap() {
        HumanoidCompatConfig.refresh();
        cachedDetected = HumanoidCompatDiscoveryStore.load();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        Set<ResourceLocation> targets = new HashSet<>();
        targets.addAll(HumanoidCompatConfig.forceInclude());
        if (HumanoidCompatConfig.autoDetect()) {
            targets.addAll(cachedDetected);
        }

        for (ResourceLocation entityId : targets) {
            if (HumanoidCompatConfig.isBlacklisted(entityId)) {
                continue;
            }

            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (entityType == null || entityType == EntityType.PLAYER || event.getTypeEntry().containsKey(entityType)) {
                continue;
            }

            registerIntoEvent(event, entityType);
        }
    }

    /** True when the current startup already considers this ID an EFPI Humanoid target. */
    public static boolean isKnownCompatibilityTarget(ResourceLocation id) {
        if (id == null || HumanoidCompatConfig.isBlacklisted(id)) {
            return false;
        }
        return runtimeDetected.contains(id)
                || HumanoidCompatConfig.forceInclude().contains(id)
                || (HumanoidCompatConfig.autoDetect() && cachedDetected.contains(id));
    }

    /**
     * Registers a freshly client-discovered type for this JVM as well as the
     * persistent discovery cache. On an integrated server this runs before a
     * world is opened, so server-side entities created later receive the patch on
     * the first run rather than requiring a restart.
     */
    public static synchronized boolean registerDiscoveredAtRuntime(EntityType<?> entityType) {
        if (entityType == null || entityType == EntityType.PLAYER) {
            return false;
        }

        ResourceLocation id = EntityType.getKey(entityType);
        if (id == null || HumanoidCompatConfig.isBlacklisted(id)) {
            return false;
        }

        runtimeDetected.add(id);
        Set<ResourceLocation> persisted = new HashSet<>(runtimeDetected);
        persisted.addAll(cachedDetected);
        persisted.removeIf(HumanoidCompatConfig::isBlacklisted);
        cachedDetected = Set.copyOf(persisted);
        HumanoidCompatDiscoveryStore.save(cachedDetected);

        try {
            Armatures.registerEntityTypeArmature(entityType, Armatures.BIPED);
            EntityPatchProvider.putCustomEntityPatch(entityType, ignored -> LightweightHumanoidPatch::new);
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to install runtime Humanoid compatibility patch for {}", id, throwable);
            return false;
        }
    }

    public static boolean isCompatibilityBlacklisted(EntityType<?> entityType) {
        return entityType == null || HumanoidCompatConfig.isBlacklisted(EntityType.getKey(entityType));
    }

    public static synchronized void replaceDetectedCache(Set<ResourceLocation> detected) {
        Set<ResourceLocation> filtered = new HashSet<>(detected);
        filtered.removeIf(HumanoidCompatConfig::isBlacklisted);
        runtimeDetected.clear();
        runtimeDetected.addAll(filtered);
        cachedDetected = Set.copyOf(filtered);
        HumanoidCompatDiscoveryStore.save(cachedDetected);
    }

    private static void registerIntoEvent(EntityPatchRegistryEvent event, EntityType<?> entityType) {
        Armatures.registerEntityTypeArmature(entityType, Armatures.BIPED);
        event.getTypeEntry().put(entityType, ignored -> LightweightHumanoidPatch::new);
    }

    private VanillaHumanoidCompat() {
    }
}
