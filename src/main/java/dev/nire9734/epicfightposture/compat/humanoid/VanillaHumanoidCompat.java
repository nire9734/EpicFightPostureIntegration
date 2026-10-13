package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.PostureServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Lightweight EF patch registration for automatically detected humanoid entities. */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class VanillaHumanoidCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<ResourceLocation, CompatibilityKind> cachedDetected = Map.of();
    private static final Map<ResourceLocation, CompatibilityKind> registeredByEfpi = new HashMap<>();

    public enum CompatibilityKind {
        VANILLA_HUMANOID,
        GECKOLIB_HUMANOID
    }

    public static void bootstrap() {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            HumanoidServerDiscoveryStore.ActiveTargets active =
                    HumanoidServerDiscoveryStore.bootstrap(HumanoidEnvironmentFingerprint.current());
            cachedDetected = buildKindMap(active.vanillaHumanoid(), active.geckoLibHumanoid());
        } else {
            HumanoidCompatDiscoveryStore.Cache cache = HumanoidCompatDiscoveryStore.load(
                    HumanoidEnvironmentFingerprint.current()
            );
            cachedDetected = buildKindMap(cache.vanillaHumanoid(), cache.geckoLibHumanoid());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static synchronized void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        registeredByEfpi.clear();

        for (Map.Entry<ResourceLocation, CompatibilityKind> target : cachedDetected.entrySet()) {
            ResourceLocation entityId = target.getKey();
            CompatibilityKind kind = target.getValue();
            if (PostureServerConfig.isBlacklisted(entityId) || !compatibilityKindEnabled(kind)) {
                continue;
            }

            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (entityType == null || entityType == EntityType.PLAYER || event.getTypeEntry().containsKey(entityType)) {
                continue;
            }

            registerIntoEvent(event, entityType);
            registeredByEfpi.put(entityId, kind);
        }
    }

    /** True only when EFPI actually owns the active lightweight registration in this JVM. */
    public static synchronized boolean isRegisteredByEfpi(ResourceLocation id) {
        return id != null && registeredByEfpi.containsKey(id);
    }

    /**
     * Returns whether EFPI's automatic compatibility is enabled for this target.
     * The cached kind fallback is intentional: on an integrated client the runtime
     * renderer registration can happen before the per-world SERVER config is loaded.
     */
    public static synchronized boolean isCompatibilityEnabled(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        CompatibilityKind kind = registeredByEfpi.get(id);
        if (kind == null) {
            kind = cachedDetected.get(id);
        }
        return kind != null && compatibilityKindEnabled(kind);
    }

    public static boolean isCompatibilityEnabled(EntityType<?> entityType) {
        return entityType != null && isCompatibilityEnabled(EntityType.getKey(entityType));
    }

    /**
     * Installs a freshly client-discovered type in the current JVM. This keeps
     * integrated single-player working on the first run, before a restart has
     * had a chance to consume the persisted discovery cache.
     *
     * <p>The client-side registration is kept even when the current SERVER config
     * disables that kind, because the config may not yet be loaded/synchronized.
     * Runtime behavior is independently gated by {@link #isCompatibilityEnabled}.</p>
     */
    public static synchronized boolean registerDiscoveredAtRuntime(
            EntityType<?> entityType,
            CompatibilityKind kind) {
        if (entityType == null || entityType == EntityType.PLAYER || kind == null) {
            return false;
        }

        ResourceLocation id = EntityType.getKey(entityType);
        if (id == null || PostureServerConfig.isBlacklisted(id)) {
            return false;
        }

        Map<ResourceLocation, CompatibilityKind> updated = new HashMap<>(cachedDetected);
        CompatibilityKind previous = updated.put(id, kind);
        if (previous != null && previous != kind) {
            LOGGER.warn("EFPI humanoid renderer kind changed for {} from {} to {}; using the latest scan",
                    id, previous, kind);
        }
        cachedDetected = Map.copyOf(updated);
        saveClientCache();

        try {
            Armatures.registerEntityTypeArmature(entityType, Armatures.BIPED);
            EntityPatchProvider.putCustomEntityPatch(entityType, ignored -> LightweightHumanoidPatch::new);
            registeredByEfpi.put(id, kind);
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to install runtime Humanoid compatibility patch for {}", id, throwable);
            return false;
        }
    }

    public static boolean isCompatibilityBlacklisted(EntityType<?> entityType) {
        return entityType == null || PostureServerConfig.isBlacklisted(EntityType.getKey(entityType));
    }

    /** Stores every detected target, including IDs the server may disable or blacklist later. */
    public static synchronized void replaceDetectedCache(
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> geckoLibHumanoid) {
        cachedDetected = buildKindMap(vanillaHumanoid, geckoLibHumanoid);
        saveClientCache();
    }

    private static boolean compatibilityKindEnabled(CompatibilityKind kind) {
        return switch (kind) {
            case VANILLA_HUMANOID -> PostureServerConfig.vanillaHumanoidCompatibilityEnabled();
            case GECKOLIB_HUMANOID -> PostureServerConfig.geckoLibHumanoidCompatibilityEnabled();
        };
    }

    private static Map<ResourceLocation, CompatibilityKind> buildKindMap(
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> geckoLibHumanoid) {
        Set<ResourceLocation> vanilla = vanillaHumanoid == null ? Set.of() : vanillaHumanoid;
        Set<ResourceLocation> gecko = geckoLibHumanoid == null ? Set.of() : geckoLibHumanoid;
        Set<ResourceLocation> overlap = new HashSet<>(vanilla);
        overlap.retainAll(gecko);
        if (!overlap.isEmpty()) {
            LOGGER.warn("EFPI ignored {} humanoid target(s) with ambiguous renderer classification", overlap.size());
        }

        Map<ResourceLocation, CompatibilityKind> result = new HashMap<>();
        for (ResourceLocation id : vanilla) {
            if (id != null && !overlap.contains(id)) {
                result.put(id, CompatibilityKind.VANILLA_HUMANOID);
            }
        }
        for (ResourceLocation id : gecko) {
            if (id != null && !overlap.contains(id)) {
                result.put(id, CompatibilityKind.GECKOLIB_HUMANOID);
            }
        }
        return Map.copyOf(result);
    }

    private static void saveClientCache() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        Set<ResourceLocation> vanilla = new HashSet<>();
        Set<ResourceLocation> gecko = new HashSet<>();
        for (Map.Entry<ResourceLocation, CompatibilityKind> entry : cachedDetected.entrySet()) {
            if (entry.getValue() == CompatibilityKind.VANILLA_HUMANOID) {
                vanilla.add(entry.getKey());
            } else if (entry.getValue() == CompatibilityKind.GECKOLIB_HUMANOID) {
                gecko.add(entry.getKey());
            }
        }

        HumanoidCompatDiscoveryStore.save(
                HumanoidEnvironmentFingerprint.current(),
                vanilla,
                gecko
        );
    }

    private static void registerIntoEvent(EntityPatchRegistryEvent event, EntityType<?> entityType) {
        Armatures.registerEntityTypeArmature(entityType, Armatures.BIPED);
        event.getTypeEntry().put(entityType, ignored -> LightweightHumanoidPatch::new);
    }

    private VanillaHumanoidCompat() {
    }
}
