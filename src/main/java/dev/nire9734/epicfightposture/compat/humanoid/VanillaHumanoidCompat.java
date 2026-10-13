package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.PostureServerConfig;
import dev.nire9734.epicfightposture.compat.spider.LightweightSpiderPatch;
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

/** Lightweight EF patch registration for automatically detected renderer-compatible entities. */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class VanillaHumanoidCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<ResourceLocation, CompatibilityKind> cachedDetected = Map.of();
    private static volatile Set<ResourceLocation> externallyPatched = Set.of();
    private static final Map<ResourceLocation, CompatibilityKind> registeredByEfpi = new HashMap<>();

    public enum CompatibilityKind {
        VANILLA_HUMANOID,
        GECKOLIB_HUMANOID,
        VANILLA_SPIDER
    }

    public static void bootstrap() {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            HumanoidServerDiscoveryStore.ActiveTargets active =
                    HumanoidServerDiscoveryStore.bootstrap(HumanoidEnvironmentFingerprint.current());
            cachedDetected = buildKindMap(
                    active.vanillaHumanoid(),
                    active.geckoLibHumanoid(),
                    active.vanillaSpider()
            );
        } else {
            HumanoidCompatDiscoveryStore.Cache cache = HumanoidCompatDiscoveryStore.load(
                    HumanoidEnvironmentFingerprint.current()
            );
            cachedDetected = buildKindMap(
                    cache.vanillaHumanoid(),
                    cache.geckoLibHumanoid(),
                    cache.vanillaSpider()
            );
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static synchronized void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        registeredByEfpi.clear();

        // Snapshot everything that already owns an Epic Fight patch before EFPI
        // adds its lightweight compatibility patches. At LOWEST priority this also
        // includes patches registered by other mods during the same registry event.
        Set<ResourceLocation> existingPatchIds = new HashSet<>();
        for (EntityType<?> entityType : event.getTypeEntry().keySet()) {
            ResourceLocation id = EntityType.getKey(entityType);
            if (id != null) {
                existingPatchIds.add(id);
            }
        }
        externallyPatched = Set.copyOf(existingPatchIds);

        for (Map.Entry<ResourceLocation, CompatibilityKind> target : cachedDetected.entrySet()) {
            ResourceLocation entityId = target.getKey();
            CompatibilityKind kind = target.getValue();
            if (PostureServerConfig.isBlacklisted(entityId)
                    || externallyPatched.contains(entityId)
                    || !compatibilityKindEnabled(kind)) {
                continue;
            }

            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (entityType == null || entityType == EntityType.PLAYER || event.getTypeEntry().containsKey(entityType)) {
                continue;
            }

            registerIntoEvent(event, entityType, kind);
            registeredByEfpi.put(entityId, kind);
        }
    }

    /** True only when EFPI actually owns the active lightweight registration in this JVM. */
    public static synchronized boolean isRegisteredByEfpi(ResourceLocation id) {
        return id != null && registeredByEfpi.containsKey(id);
    }

    /** True when Epic Fight or another mod already owned a patch before EFPI registration. */
    public static boolean isExternallyPatched(ResourceLocation id) {
        return id != null && externallyPatched.contains(id);
    }

    /** Immutable snapshot used by client renderer discovery to exclude existing patches entirely. */
    public static Set<ResourceLocation> externallyPatchedIds() {
        return externallyPatched;
    }

    /** Returns whether the server-authoritative switch permits this EFPI-owned lightweight patch. */
    public static synchronized boolean isCompatibilityEnabled(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        CompatibilityKind kind = registeredByEfpi.get(id);
        return kind != null && compatibilityKindEnabled(kind);
    }

    public static boolean isCompatibilityEnabled(EntityType<?> entityType) {
        return entityType != null && isCompatibilityEnabled(EntityType.getKey(entityType));
    }

    public static synchronized boolean registerDiscoveredAtRuntime(
            EntityType<?> entityType,
            CompatibilityKind kind) {
        if (entityType == null || entityType == EntityType.PLAYER || kind == null) {
            return false;
        }

        ResourceLocation id = EntityType.getKey(entityType);
        if (id == null || PostureServerConfig.isBlacklisted(id) || externallyPatched.contains(id)) {
            return false;
        }

        Map<ResourceLocation, CompatibilityKind> updated = new HashMap<>(cachedDetected);
        CompatibilityKind previous = updated.put(id, kind);
        if (previous != null && previous != kind) {
            LOGGER.warn("EFPI renderer compatibility kind changed for {} from {} to {}; using the latest scan",
                    id, previous, kind);
        }
        cachedDetected = Map.copyOf(updated);
        saveClientCache();

        try {
            registerRuntime(entityType, kind);
            registeredByEfpi.put(id, kind);
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to install runtime automatic compatibility patch for {}", id, throwable);
            return false;
        }
    }

    public static boolean isCompatibilityBlacklisted(EntityType<?> entityType) {
        return entityType == null || PostureServerConfig.isBlacklisted(EntityType.getKey(entityType));
    }

    /** Stores every detected target, including IDs the server may disable or blacklist later. */
    public static synchronized void replaceDetectedCache(
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> geckoLibHumanoid,
            Set<ResourceLocation> vanillaSpider) {
        cachedDetected = buildKindMap(vanillaHumanoid, geckoLibHumanoid, vanillaSpider);
        saveClientCache();
    }

    private static boolean compatibilityKindEnabled(CompatibilityKind kind) {
        return switch (kind) {
            case VANILLA_HUMANOID -> PostureServerConfig.vanillaHumanoidCompatibilityEnabled();
            case GECKOLIB_HUMANOID -> PostureServerConfig.geckoLibHumanoidCompatibilityEnabled();
            case VANILLA_SPIDER -> PostureServerConfig.vanillaSpiderCompatibilityEnabled();
        };
    }

    private static Map<ResourceLocation, CompatibilityKind> buildKindMap(
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> geckoLibHumanoid,
            Set<ResourceLocation> vanillaSpider) {
        Set<ResourceLocation> vanilla = vanillaHumanoid == null ? Set.of() : vanillaHumanoid;
        Set<ResourceLocation> gecko = geckoLibHumanoid == null ? Set.of() : geckoLibHumanoid;
        Set<ResourceLocation> spider = vanillaSpider == null ? Set.of() : vanillaSpider;

        Set<ResourceLocation> ambiguous = new HashSet<>();
        collectOverlap(ambiguous, vanilla, gecko);
        collectOverlap(ambiguous, vanilla, spider);
        collectOverlap(ambiguous, gecko, spider);
        if (!ambiguous.isEmpty()) {
            LOGGER.warn("EFPI ignored {} target(s) with ambiguous renderer classification", ambiguous.size());
        }

        Map<ResourceLocation, CompatibilityKind> result = new HashMap<>();
        addKind(result, vanilla, ambiguous, CompatibilityKind.VANILLA_HUMANOID);
        addKind(result, gecko, ambiguous, CompatibilityKind.GECKOLIB_HUMANOID);
        addKind(result, spider, ambiguous, CompatibilityKind.VANILLA_SPIDER);
        return Map.copyOf(result);
    }

    private static void collectOverlap(
            Set<ResourceLocation> target,
            Set<ResourceLocation> first,
            Set<ResourceLocation> second) {
        Set<ResourceLocation> overlap = new HashSet<>(first);
        overlap.retainAll(second);
        target.addAll(overlap);
    }

    private static void addKind(
            Map<ResourceLocation, CompatibilityKind> result,
            Set<ResourceLocation> ids,
            Set<ResourceLocation> ambiguous,
            CompatibilityKind kind) {
        for (ResourceLocation id : ids) {
            if (id != null && !ambiguous.contains(id)) {
                result.put(id, kind);
            }
        }
    }

    private static void saveClientCache() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        Set<ResourceLocation> vanilla = new HashSet<>();
        Set<ResourceLocation> gecko = new HashSet<>();
        Set<ResourceLocation> spider = new HashSet<>();
        for (Map.Entry<ResourceLocation, CompatibilityKind> entry : cachedDetected.entrySet()) {
            switch (entry.getValue()) {
                case VANILLA_HUMANOID -> vanilla.add(entry.getKey());
                case GECKOLIB_HUMANOID -> gecko.add(entry.getKey());
                case VANILLA_SPIDER -> spider.add(entry.getKey());
            }
        }

        HumanoidCompatDiscoveryStore.save(
                HumanoidEnvironmentFingerprint.current(),
                vanilla,
                gecko,
                spider
        );
    }

    private static void registerRuntime(EntityType<?> entityType, CompatibilityKind kind) {
        switch (kind) {
            case VANILLA_HUMANOID, GECKOLIB_HUMANOID -> {
                Armatures.registerEntityTypeArmature(entityType, Armatures.BIPED);
                EntityPatchProvider.putCustomEntityPatch(entityType, ignored -> LightweightHumanoidPatch::new);
            }
            case VANILLA_SPIDER -> {
                Armatures.registerEntityTypeArmature(entityType, Armatures.SPIDER);
                EntityPatchProvider.putCustomEntityPatch(entityType, ignored -> LightweightSpiderPatch::new);
            }
        }
    }

    private static void registerIntoEvent(
            EntityPatchRegistryEvent event,
            EntityType<?> entityType,
            CompatibilityKind kind) {
        switch (kind) {
            case VANILLA_HUMANOID, GECKOLIB_HUMANOID -> {
                Armatures.registerEntityTypeArmature(entityType, Armatures.BIPED);
                event.getTypeEntry().put(entityType, ignored -> LightweightHumanoidPatch::new);
            }
            case VANILLA_SPIDER -> {
                Armatures.registerEntityTypeArmature(entityType, Armatures.SPIDER);
                event.getTypeEntry().put(entityType, ignored -> LightweightSpiderPatch::new);
            }
        }
    }

    private VanillaHumanoidCompat() {
    }
}
