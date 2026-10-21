package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import dev.nire9734.epicfightposture.compat.humanoid.runtime.epicfight.HumanoidArmatureFactory;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared discovery/registration pipeline for all non-HumanoidModel humanoid backends. */
public final class HumanoidBackendRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, HumanoidRenderBackend> BACKENDS = new LinkedHashMap<>();
    private static final Map<EntityType<?>, PreparedHumanoidModel> PREPARED = new HashMap<>();
    private static Map<String, Integer> lastVerifiedByBackend = Map.of();

    public static synchronized void clear() {
        BACKENDS.clear();
        PREPARED.clear();
        lastVerifiedByBackend = Map.of();
    }

    public static synchronized void register(HumanoidRenderBackend backend) {
        if (backend == null) {
            return;
        }
        HumanoidRenderBackend previous = BACKENDS.putIfAbsent(backend.id(), backend);
        if (previous != null && previous != backend) {
            LOGGER.warn("EFPI ignored duplicate humanoid backend id {}", backend.id());
        }
    }

    public static synchronized ScanResult scan(Map<EntityType<?>, EntityRenderer<?>> renderers) {
        PREPARED.clear();
        Set<ResourceLocation> inspected = new HashSet<>();
        Set<ResourceLocation> compatible = new HashSet<>();
        Set<EntityType<?>> compatibleTypes = new HashSet<>();
        int backendInspectionFailures = 0;
        int ambiguousTargets = 0;

        for (Map.Entry<EntityType<?>, EntityRenderer<?>> entry : renderers.entrySet()) {
            EntityType<?> entityType = entry.getKey();
            EntityRenderer<?> renderer = entry.getValue();
            if (entityType == null || entityType == EntityType.PLAYER || renderer == null) {
                continue;
            }

            ResourceLocation entityId = EntityType.getKey(entityType);
            if (entityId == null) {
                continue;
            }

            List<PreparedHumanoidModel> candidates = new ArrayList<>();
            for (HumanoidRenderBackend backend : BACKENDS.values()) {
                HumanoidRenderBackend.Inspection inspection;
                try {
                    inspection = backend.inspect(entityType, renderer);
                } catch (Throwable throwable) {
                    backendInspectionFailures++;
                    LOGGER.debug("EFPI humanoid backend {} failed while inspecting {}", backend.id(), entityId, throwable);
                    continue;
                }
                if (!inspection.matched()) {
                    continue;
                }
                inspected.add(entityId);
                if (inspection.prepared() != null) {
                    candidates.add(inspection.prepared());
                }
            }

            if (candidates.size() == 1) {
                PreparedHumanoidModel prepared = candidates.get(0);
                PREPARED.put(entityType, prepared);
                compatible.add(entityId);
                compatibleTypes.add(entityType);
            } else if (candidates.size() > 1) {
                ambiguousTargets++;
                LOGGER.debug(
                        "EFPI rejected {} because multiple humanoid backends prepared it: {}",
                        entityId,
                        candidates.stream().map(PreparedHumanoidModel::backendId).toList()
                );
            }
        }

        if (backendInspectionFailures > 0 || ambiguousTargets > 0) {
            LOGGER.warn(
                    "EFPI automatic compatibility scan skipped {} backend inspection failure(s) and {} ambiguous target(s); enable debug logging for details",
                    backendInspectionFailures,
                    ambiguousTargets
            );
        }

        return new ScanResult(Set.copyOf(inspected), Set.copyOf(compatible), Set.copyOf(compatibleTypes));
    }

    public static synchronized Set<EntityType<?>> registerRenderers(
            PatchedRenderersEvent.Add event,
            Set<EntityType<?>> entityTypes) {
        Set<EntityType<?>> alreadyPatched = new HashSet<>(EntityPatchProvider.getPatchedEntities());
        Set<EntityType<?>> verified = new HashSet<>();
        Map<String, Integer> verifiedByBackend = new LinkedHashMap<>();
        int retargetedArmatures = 0;
        int armaturePreparationFailures = 0;
        int rendererInstallationFailures = 0;

        for (EntityType<?> entityType : entityTypes) {
            PreparedHumanoidModel prepared = PREPARED.get(entityType);
            if (prepared == null) {
                continue;
            }

            ResourceLocation entityId = prepared.entityId();
            boolean oursAlready = VanillaHumanoidCompat.isRegisteredByEfpi(entityId);
            if (alreadyPatched.contains(entityType) && !oursAlready) {
                continue;
            }

            HumanoidRenderBackend backend = BACKENDS.get(prepared.backendId());
            if (backend == null) {
                LOGGER.warn("EFPI lost humanoid backend {} before renderer registration for {}",
                        prepared.backendId(), entityId);
                continue;
            }

            AssetAccessor<? extends Armature> armatureOverride = null;
            if (prepared.skeleton().isPresent()) {
                HumanoidSkeletonDescriptor descriptor = prepared.skeleton().get();
                try {
                    ResourceLocation runtimeArmatureId = new ResourceLocation(
                            EpicFightPostureIntegration.MODID,
                            "runtime_armature/" + prepared.backendId() + "/"
                                    + entityId.getNamespace() + "/" + entityId.getPath()
                    );
                    HumanoidArmature targetArmature = HumanoidArmatureFactory.create(
                            runtimeArmatureId.toString(),
                            descriptor,
                            Armatures.BIPED.get()
                    );
                    armatureOverride = HumanoidArmatureFactory.accessor(runtimeArmatureId, targetArmature);
                    retargetedArmatures++;
                } catch (Throwable throwable) {
                    armaturePreparationFailures++;
                    if (oursAlready) {
                        VanillaHumanoidCompat.rejectRuntimeCompatibility(entityType);
                    }
                    LOGGER.debug(
                            "EFPI rejected automatic humanoid compatibility for {} because target-proportioned runtime armature generation failed",
                            entityId,
                            throwable
                    );
                    continue;
                }
            }

            boolean runtimeReady;
            if (oursAlready) {
                runtimeReady = armatureOverride == null
                        || VanillaHumanoidCompat.refreshRuntimeArmature(
                                entityType,
                                VanillaHumanoidCompat.CompatibilityKind.EXTENDED_HUMANOID,
                                armatureOverride
                        );
            } else {
                runtimeReady = VanillaHumanoidCompat.registerDiscoveredAtRuntime(
                        entityType,
                        VanillaHumanoidCompat.CompatibilityKind.EXTENDED_HUMANOID,
                        armatureOverride
                );
            }
            if (!runtimeReady) {
                if (oursAlready) {
                    VanillaHumanoidCompat.rejectRuntimeCompatibility(entityType);
                }
                continue;
            }

            if (!RendererRegistrationGuard.install(
                    () -> backend.addPatchedRenderer(event, prepared),
                    () -> VanillaHumanoidCompat.rejectRuntimeCompatibility(entityType),
                    throwable -> LOGGER.debug("EFPI backend {} could not install special renderer for {}",
                            prepared.backendId(), entityId, throwable))) {
                rendererInstallationFailures++;
                continue;
            }

            verified.add(entityType);
            verifiedByBackend.merge(prepared.backendId(), 1, Integer::sum);
        }

        LinkedHashMap<String, Integer> orderedCounts = new LinkedHashMap<>();
        for (String backendId : BACKENDS.keySet()) {
            Integer count = verifiedByBackend.get(backendId);
            if (count != null && count > 0) {
                orderedCounts.put(backendId, count);
            }
        }
        lastVerifiedByBackend = Collections.unmodifiableMap(orderedCounts);

        if (armaturePreparationFailures > 0 || rendererInstallationFailures > 0) {
            LOGGER.warn(
                    "EFPI automatic compatibility skipped {} runtime armature target(s) and {} renderer target(s) due preparation errors; enable debug logging for details",
                    armaturePreparationFailures,
                    rendererInstallationFailures
            );
        }

        LOGGER.debug(
                "EFPI prepared humanoid registration: {} verified target(s), {} target-proportioned armature(s), by backend {}",
                verified.size(),
                retargetedArmatures,
                lastVerifiedByBackend
        );
        return Set.copyOf(verified);
    }

    public static synchronized PreparedHumanoidModel prepared(EntityType<?> entityType) {
        return PREPARED.get(entityType);
    }

    public static synchronized Map<String, Integer> lastVerifiedByBackend() {
        return lastVerifiedByBackend;
    }

    public record ScanResult(
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> compatible,
            Set<EntityType<?>> compatibleTypes) {
        public ScanResult {
            inspected = inspected == null ? Set.of() : Set.copyOf(inspected);
            compatible = compatible == null ? Set.of() : Set.copyOf(compatible);
            compatibleTypes = compatibleTypes == null ? Set.of() : Set.copyOf(compatibleTypes);
        }
    }

    private HumanoidBackendRegistry() {
    }
}
