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

import java.util.HashSet;
import java.util.Set;

/** Lightweight EF patch registration for vanilla-style HumanoidModel entities. */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class VanillaHumanoidCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Set<ResourceLocation> cachedDetected = Set.of();
    private static final Set<ResourceLocation> runtimeDetected = new HashSet<>();
    private static final Set<ResourceLocation> registeredByEfpi = new HashSet<>();

    public static void bootstrap() {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            cachedDetected = HumanoidServerDiscoveryStore.bootstrap(HumanoidEnvironmentFingerprint.current());
        } else {
            cachedDetected = HumanoidCompatDiscoveryStore.load();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static synchronized void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        registeredByEfpi.clear();

        for (ResourceLocation entityId : cachedDetected) {
            if (PostureServerConfig.isBlacklisted(entityId)) {
                continue;
            }

            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (entityType == null || entityType == EntityType.PLAYER || event.getTypeEntry().containsKey(entityType)) {
                continue;
            }

            registerIntoEvent(event, entityType);
            registeredByEfpi.add(entityId);
        }
    }

    /** True only when EFPI actually owns the active lightweight registration in this JVM. */
    public static synchronized boolean isRegisteredByEfpi(ResourceLocation id) {
        return id != null && registeredByEfpi.contains(id);
    }

    /**
     * Installs a freshly client-discovered type in the current JVM. This keeps
     * integrated single-player working on the first run, before a restart has
     * had a chance to consume the persisted discovery cache.
     */
    public static synchronized boolean registerDiscoveredAtRuntime(EntityType<?> entityType) {
        if (entityType == null || entityType == EntityType.PLAYER) {
            return false;
        }

        ResourceLocation id = EntityType.getKey(entityType);
        if (id == null || PostureServerConfig.isBlacklisted(id)) {
            return false;
        }

        runtimeDetected.add(id);
        Set<ResourceLocation> persisted = new HashSet<>(cachedDetected);
        persisted.addAll(runtimeDetected);
        cachedDetected = Set.copyOf(persisted);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            HumanoidCompatDiscoveryStore.save(cachedDetected);
        }

        try {
            Armatures.registerEntityTypeArmature(entityType, Armatures.BIPED);
            EntityPatchProvider.putCustomEntityPatch(entityType, ignored -> LightweightHumanoidPatch::new);
            registeredByEfpi.add(id);
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to install runtime Humanoid compatibility patch for {}", id, throwable);
            return false;
        }
    }

    public static boolean isCompatibilityBlacklisted(EntityType<?> entityType) {
        return entityType == null || PostureServerConfig.isBlacklisted(EntityType.getKey(entityType));
    }

    /** Stores every detected HumanoidModel ID, including IDs the server may blacklist later. */
    public static synchronized void replaceDetectedCache(Set<ResourceLocation> detected) {
        runtimeDetected.clear();
        runtimeDetected.addAll(detected);
        cachedDetected = Set.copyOf(detected);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            HumanoidCompatDiscoveryStore.save(cachedDetected);
        }
    }

    private static void registerIntoEvent(EntityPatchRegistryEvent event, EntityType<?> entityType) {
        Armatures.registerEntityTypeArmature(entityType, Armatures.BIPED);
        event.getTypeEntry().put(entityType, ignored -> LightweightHumanoidPatch::new);
    }

    private VanillaHumanoidCompat() {
    }
}
