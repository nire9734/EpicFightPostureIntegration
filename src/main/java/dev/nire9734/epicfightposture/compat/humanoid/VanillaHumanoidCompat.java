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
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.api.model.Armature;
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
    private static final Map<ResourceLocation, AssetAccessor<? extends Armature>> runtimeArmatures = new HashMap<>();
    private static final Set<ResourceLocation> runtimeRejected = new HashSet<>();

    public enum CompatibilityKind {
        VANILLA_HUMANOID,
        EXTENDED_HUMANOID,
        VANILLA_SPIDER
    }

    public static void bootstrap() {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            HumanoidServerDiscoveryStore.ActiveTargets active =
                    HumanoidServerDiscoveryStore.bootstrap(HumanoidEnvironmentFingerprint.current());
            cachedDetected = buildKindMap(
                    active.vanillaHumanoid(),
                    active.extendedHumanoid(),
                    active.vanillaSpider()
            );
        } else {
            // Client renderer compatibility is rediscovered and validated every launch.
            // The current result stays in memory only; no client discovery file is needed.
            cachedDetected = Map.of();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static synchronized void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        Map<ResourceLocation, CompatibilityKind> previousOwnership = new HashMap<>(registeredByEfpi);
        registeredByEfpi.clear();
        runtimeRejected.clear();

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

        for (Map.Entry<ResourceLocation, CompatibilityKind> target : cachedDetected.entrySet()) {
            ResourceLocation entityId = target.getKey();
            CompatibilityKind kind = target.getValue();
            if (PostureServerConfig.isBlacklisted(entityId)
                    || !PostureServerConfig.isAutomaticCompatibilityAllowed(entityId)
                    || !compatibilityKindEnabled(kind)) {
                continue;
            }

            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (entityType == null || entityType == EntityType.PLAYER) {
                continue;
            }

            if (event.getTypeEntry().containsKey(entityType)) {
                // Epic Fight can recreate this event without restarting Minecraft.
                // Its map may still hold our previous entry; it is not an addon.
                if (previousOwnership.get(entityId) == kind
                        && EpicFightPatchOwnership.isOwnedEventRegistration(
                                entityType, event.getTypeEntry().get(entityType))) {
                    registeredByEfpi.put(entityId, kind);
                    existingPatchIds.remove(entityId);
                }
                continue;
            }

            EpicFightPatchOwnership.State state = EpicFightPatchOwnership.state(entityType);
            if (previousOwnership.get(entityId) == kind
                    && state == EpicFightPatchOwnership.State.OWNED) {
                // A custom runtime provider from the previous world is still
                // installed. Keep its ownership rather than classing it as foreign.
                registeredByEfpi.put(entityId, kind);
                existingPatchIds.remove(entityId);
                continue;
            }
            if (existingPatchIds.contains(entityId) || state != EpicFightPatchOwnership.State.MISSING) {
                continue;
            }

            // Epic Fight 1.20.1 does not expose CUSTOM_CAPABILITIES through
            // getPatchedEntities(). Respect addon-owned custom patches explicitly;
            // replacing one can invalidate the addon's patched renderer contract.
            if (EpicFightPatchOwnership.hasCustomPatch(entityType)) {
                existingPatchIds.add(entityId);
                LOGGER.debug("EFPI skipped automatic compatibility for {} because an Epic Fight custom patch is already registered",
                        entityId);
                continue;
            }

            registerIntoEvent(event, entityType, kind);
            EpicFightPatchOwnership.recordEventRegistration(
                    entityType, event.getTypeEntry().get(entityType));
            registeredByEfpi.put(entityId, kind);
        }
        externallyPatched = Set.copyOf(existingPatchIds);
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

    /** Returns whether server-authoritative settings permit this EFPI-owned lightweight patch. */
    public static synchronized boolean isCompatibilityEnabled(ResourceLocation id) {
        if (id == null || runtimeRejected.contains(id)) {
            return false;
        }
        CompatibilityKind kind = registeredByEfpi.get(id);
        return kind != null
                && !PostureServerConfig.isExplicitlyBlacklisted(id)
                && PostureServerConfig.isAutomaticCompatibilityAllowed(id)
                && compatibilityKindEnabled(kind);
    }

    public static boolean isCompatibilityEnabled(EntityType<?> entityType) {
        return entityType != null && isCompatibilityEnabled(EntityType.getKey(entityType));
    }

    public static synchronized boolean registerDiscoveredAtRuntime(
            EntityType<?> entityType,
            CompatibilityKind kind) {
        return registerDiscoveredAtRuntime(entityType, kind, null);
    }

    /**
     * Installs a client/runtime automatic-compatibility patch with an optional
     * model-specific armature. Dedicated-server registry bootstrap intentionally
     * keeps using the canonical BIPED/SPIDER armatures until body-profile data is
     * part of the trusted discovery protocol.
     */
    public static synchronized boolean registerDiscoveredAtRuntime(
            EntityType<?> entityType,
            CompatibilityKind kind,
            AssetAccessor<? extends Armature> armatureOverride) {
        if (entityType == null || entityType == EntityType.PLAYER || kind == null) {
            return false;
        }

        ResourceLocation id = EntityType.getKey(entityType);
        // Client discovery happens before a multiplayer server can synchronize its
        // policy. Prepare every safe renderer target now; synchronized settings
        // decide whether the lightweight patch is allowed to act after login.
        if (id == null || externallyPatched.contains(id)) {
            return false;
        }

        EpicFightPatchOwnership.State state = EpicFightPatchOwnership.state(entityType);
        if (state == EpicFightPatchOwnership.State.UNKNOWN) {
            return false;
        }
        // An addon may have replaced an EFPI patch between world loads.
        // Identity-based ownership checking ensures we never overwrite it.
        if (state == EpicFightPatchOwnership.State.FOREIGN) {
            Set<ResourceLocation> updatedExternal = new HashSet<>(externallyPatched);
            updatedExternal.add(id);
            externallyPatched = Set.copyOf(updatedExternal);
            LOGGER.debug("EFPI skipped automatic compatibility for {} because another Epic Fight patch owns it",
                    id);
            return false;
        }

        CompatibilityKind previous = registeredByEfpi.get(id);
        if (previous != null && previous != kind && state == EpicFightPatchOwnership.State.OWNED) {
            // The patch class/armature is tied to the earlier renderer kind.
            runtimeRejected.add(id);
            return false;
        }

        try {
            if (state == EpicFightPatchOwnership.State.MISSING) {
                registerRuntime(entityType, kind, armatureOverride);
                if (!EpicFightPatchOwnership.recordRuntimeRegistration(entityType)) {
                    runtimeRejected.add(id);
                    return false;
                }
                LOGGER.debug("EFPI installed/recovered runtime provider for {}", id);
            } else {
                Armatures.registerEntityTypeArmature(entityType,
                        kind == CompatibilityKind.VANILLA_SPIDER ? Armatures.SPIDER
                                : armatureOverride != null ? armatureOverride : Armatures.BIPED);
            }
            registeredByEfpi.put(id, kind);
            runtimeRejected.remove(id);
            if (armatureOverride != null) {
                runtimeArmatures.put(id, armatureOverride);
            } else if (kind == CompatibilityKind.VANILLA_SPIDER
                    || kind == CompatibilityKind.VANILLA_HUMANOID) {
                runtimeArmatures.remove(id);
            }

            Map<ResourceLocation, CompatibilityKind> updated = new HashMap<>(cachedDetected);
            CompatibilityKind previousDetected = updated.put(id, kind);
            if (previousDetected != null && previousDetected != kind) {
                LOGGER.debug("EFPI renderer compatibility kind changed for {} from {} to {}; using the latest scan",
                        id, previousDetected, kind);
            }
            cachedDetected = Map.copyOf(updated);
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to install runtime automatic compatibility patch for {}", id, throwable);
            return false;
        }
    }

    /** Revalidate provider ownership and recover it if a world reload cleared it. */
    public static synchronized boolean refreshRuntimeArmature(
            EntityType<?> entityType,
            CompatibilityKind kind,
            AssetAccessor<? extends Armature> armatureOverride) {
        if (entityType == null || entityType == EntityType.PLAYER
                || kind == null || armatureOverride == null) {
            return false;
        }

        ResourceLocation id = EntityType.getKey(entityType);
        if (id == null || externallyPatched.contains(id) || registeredByEfpi.get(id) != kind) {
            return false;
        }

        return registerDiscoveredAtRuntime(entityType, kind, armatureOverride);
    }

    /**
     * Epic Fight can rebuild its provider maps on integrated-server world
     * changes without rebuilding the client renderers. Rebind only a missing
     * EFPI-owned provider; never replace an unknown or addon-owned one.
     */
    public static synchronized void recoverClientRegistrationsOnWorldJoin() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        int recovered = 0;
        for (Map.Entry<ResourceLocation, CompatibilityKind> entry : Map.copyOf(registeredByEfpi).entrySet()) {
            ResourceLocation id = entry.getKey();
            if (runtimeRejected.contains(id)) {
                continue;
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (type == null) {
                continue;
            }
            EpicFightPatchOwnership.State state = EpicFightPatchOwnership.state(type);
            if (state == EpicFightPatchOwnership.State.MISSING) {
                if (registerDiscoveredAtRuntime(type, entry.getValue(), runtimeArmatures.get(id))) {
                    recovered++;
                } else {
                    runtimeRejected.add(id);
                }
            } else if (state == EpicFightPatchOwnership.State.FOREIGN
                    || state == EpicFightPatchOwnership.State.UNKNOWN) {
                runtimeRejected.add(id);
            }
        }
        if (recovered > 0) {
            LOGGER.info("EFPI recovered {} automatic compatibility provider(s) after world join", recovered);
        }
    }

    /**
     * Leaves an already-installed EFPI patch physically registered but inert.
     * This is used when a later renderer scan cannot safely rebuild the matching
     * runtime armature. Retaining ownership lets a subsequent scan retry cleanly.
     */
    public static synchronized void rejectRuntimeCompatibility(EntityType<?> entityType) {
        if (entityType == null) {
            return;
        }
        ResourceLocation id = EntityType.getKey(entityType);
        if (id != null && registeredByEfpi.containsKey(id)) {
            runtimeRejected.add(id);
        }
    }

    /**
     * Match client-owned lightweight patches to the renderers that actually passed
     * this scan. A previous failed/replaced model stays inert, while a later
     * successful renderer scan can reactivate an existing EFPI-owned patch.
     *
     * <p>Only used on the client. Server-side authority and ownership are unchanged.</p>
     */
    public static synchronized void reconcileClientRuntimeCompatibility(
            Set<ResourceLocation> verified) {
        ClientRuntimeCompatibilityReconciler.reconcile(
                registeredByEfpi.keySet(), verified, runtimeRejected);
    }

    public static boolean isCompatibilityBlacklisted(EntityType<?> entityType) {
        return entityType == null || PostureServerConfig.isBlacklisted(EntityType.getKey(entityType));
    }

    /** Stores renderer targets that passed the current client compatibility preparation in memory. */
    public static synchronized void replaceDetectedCache(
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> extendedHumanoid,
            Set<ResourceLocation> vanillaSpider) {
        cachedDetected = buildKindMap(vanillaHumanoid, extendedHumanoid, vanillaSpider);
    }

    private static boolean compatibilityKindEnabled(CompatibilityKind kind) {
        return switch (kind) {
            case VANILLA_HUMANOID -> PostureServerConfig.vanillaHumanoidCompatibilityEnabled();
            case EXTENDED_HUMANOID -> PostureServerConfig.extendedHumanoidCompatibilityEnabled();
            case VANILLA_SPIDER -> PostureServerConfig.vanillaSpiderCompatibilityEnabled();
        };
    }

    private static Map<ResourceLocation, CompatibilityKind> buildKindMap(
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> extendedHumanoid,
            Set<ResourceLocation> vanillaSpider) {
        Set<ResourceLocation> vanilla = vanillaHumanoid == null ? Set.of() : vanillaHumanoid;
        Set<ResourceLocation> extended = extendedHumanoid == null ? Set.of() : extendedHumanoid;
        Set<ResourceLocation> spider = vanillaSpider == null ? Set.of() : vanillaSpider;

        Set<ResourceLocation> ambiguous = new HashSet<>();
        collectOverlap(ambiguous, vanilla, extended);
        collectOverlap(ambiguous, vanilla, spider);
        collectOverlap(ambiguous, extended, spider);
        if (!ambiguous.isEmpty()) {
            LOGGER.warn("EFPI ignored {} target(s) with ambiguous renderer classification", ambiguous.size());
        }

        Map<ResourceLocation, CompatibilityKind> result = new HashMap<>();
        addKind(result, vanilla, ambiguous, CompatibilityKind.VANILLA_HUMANOID);
        addKind(result, extended, ambiguous, CompatibilityKind.EXTENDED_HUMANOID);
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

    private static void registerRuntime(
            EntityType<?> entityType,
            CompatibilityKind kind,
            AssetAccessor<? extends Armature> armatureOverride) {
        switch (kind) {
            case VANILLA_HUMANOID, EXTENDED_HUMANOID -> {
                Armatures.registerEntityTypeArmature(
                        entityType,
                        armatureOverride != null ? armatureOverride : Armatures.BIPED
                );
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
            case VANILLA_HUMANOID, EXTENDED_HUMANOID -> {
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
