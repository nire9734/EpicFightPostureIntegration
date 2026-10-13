package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.SelfAccessor;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Optional GeckoLib 4.x bridge. Loaded only when the geckolib mod is present. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class GeckoHumanoidClientCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<EntityType<?>, AssetAccessor<SkinnedMesh>> RUNTIME_MESHES = new HashMap<>();

    public static void installDiscovery() {
        HumanoidClientDiscovery.installAdditionalScanner(GeckoHumanoidClientCompat::scan);
    }

    public static HumanoidClientDiscovery.ScanSnapshot scan(Map<EntityType<?>, EntityRenderer<?>> renderers) {
        Set<ResourceLocation> inspected = new HashSet<>();
        Set<ResourceLocation> compatible = new HashSet<>();
        Set<EntityType<?>> compatibleTypes = new HashSet<>();
        RUNTIME_MESHES.clear();

        for (Map.Entry<EntityType<?>, EntityRenderer<?>> entry : renderers.entrySet()) {
            EntityType<?> entityType = entry.getKey();
            EntityRenderer<?> sourceRenderer = entry.getValue();
            if (entityType == null || entityType == EntityType.PLAYER
                    || !(sourceRenderer instanceof GeoEntityRenderer<?> geoRenderer)) {
                continue;
            }

            ResourceLocation entityId = EntityType.getKey(entityType);
            if (entityId == null) continue;
            inspected.add(entityId);

            try {
                GeoModel geoModel = geoRenderer.getGeoModel();
                ResourceLocation modelLocation = geoModel.getModelResource(null);
                BakedGeoModel bakedModel = geoModel.getBakedModel(modelLocation);

                GeckoHumanoidModelAdapter.ProbeResult probe = GeckoHumanoidModelAdapter.probe(bakedModel);
                if (!probe.supported()) {
                    LOGGER.debug("EFPI Gecko humanoid probe rejected {}: {}", entityId, probe.reason());
                    continue;
                }

                compatible.add(entityId);
                compatibleTypes.add(entityType);

                GeckoHumanoidModelAdapter.Result result = GeckoHumanoidModelAdapter.analyseAndBake(bakedModel);
                if (!result.supported() || result.mesh() == null) {
                    LOGGER.warn(
                            "EFPI detected Gecko humanoid {} but could not prepare its special-state mesh: {}. "
                                    + "Posture compatibility remains enabled.",
                            entityId,
                            result.reason()
                    );
                    continue;
                }

                ResourceLocation runtimeMeshId = new ResourceLocation(
                        EpicFightPostureIntegration.MODID,
                        "runtime_gecko/" + entityId.getNamespace() + "/" + entityId.getPath()
                );
                RUNTIME_MESHES.put(entityType, SelfAccessor.create(runtimeMeshId, result.mesh()));
            } catch (Throwable throwable) {
                LOGGER.warn("EFPI could not inspect GeckoLib model for {}", entityId, throwable);
            }
        }

        return new HumanoidClientDiscovery.ScanSnapshot(
                Set.copyOf(inspected),
                Set.of(),
                Set.copyOf(compatible),
                Set.of(),
                Set.of(),
                Set.copyOf(compatibleTypes),
                Set.of()
        );
    }

    public static void registerRenderers(
            PatchedRenderersEvent.Add event,
            Set<EntityType<?>> entityTypes) {
        Set<EntityType<?>> alreadyPatched = new HashSet<>(EntityPatchProvider.getPatchedEntities());
        int patched = 0;
        int rendered = 0;

        for (EntityType<?> entityType : entityTypes) {
            ResourceLocation entityId = EntityType.getKey(entityType);
            if (entityId == null) continue;

            boolean oursAlready = VanillaHumanoidCompat.isRegisteredByEfpi(entityId);
            if (alreadyPatched.contains(entityType) && !oursAlready) {
                continue;
            }

            if (!oursAlready && !VanillaHumanoidCompat.registerDiscoveredAtRuntime(
                    entityType,
                    VanillaHumanoidCompat.CompatibilityKind.GECKOLIB_HUMANOID)) {
                continue;
            }
            patched++;

            AssetAccessor<SkinnedMesh> mesh = RUNTIME_MESHES.get(entityType);
            if (mesh == null) {
                LOGGER.warn(
                        "EFPI registered Gecko humanoid posture patch for {}, but no special-state mesh is available",
                        entityId
                );
                continue;
            }

            event.addPatchedEntityRenderer(
                    entityType,
                    type -> new SpecialStateGeckoRenderer(mesh, event.getContext())
            );
            rendered++;
        }

        LOGGER.info(
                "EFPI auto-detected {} GeckoLib humanoid entity type(s): {} patched, {} special renderer(s)",
                entityTypes.size(), patched, rendered
        );
    }

    private GeckoHumanoidClientCompat() {
    }
}
