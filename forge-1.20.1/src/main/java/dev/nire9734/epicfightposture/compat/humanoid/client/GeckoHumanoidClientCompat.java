package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
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

import java.util.Optional;

/** Optional GeckoLib 4.x humanoid backend. Loaded only when GeckoLib is present. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class GeckoHumanoidClientCompat implements HumanoidRenderBackend {
    public static final GeckoHumanoidClientCompat INSTANCE = new GeckoHumanoidClientCompat();
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String id() {
        return "geckolib";
    }

    @Override
    public Inspection inspect(EntityType<?> entityType, EntityRenderer<?> sourceRenderer) {
        if (!(sourceRenderer instanceof GeoEntityRenderer<?> geoRenderer)) {
            return Inspection.unmatched();
        }

        ResourceLocation entityId = EntityType.getKey(entityType);
        if (entityId == null) {
            return Inspection.rejected();
        }

        try {
            GeoModel geoModel = geoRenderer.getGeoModel();
            if (geoModel == null) {
                LOGGER.debug("EFPI Gecko humanoid skipped {} because the renderer has no GeoModel", entityId);
                return Inspection.rejected();
            }

            ResourceLocation modelLocation = resolveStaticModelLocation(geoModel, entityId);
            if (modelLocation == null) {
                return Inspection.rejected();
            }

            BakedGeoModel bakedModel = geoModel.getBakedModel(modelLocation);
            if (bakedModel == null) {
                LOGGER.debug(
                        "EFPI Gecko humanoid skipped {} because model {} did not resolve a baked bind model",
                        entityId,
                        modelLocation
                );
                return Inspection.rejected();
            }

            SkeletonKey key = new SkeletonKey(
                    entityId.toString(),
                    geoModel.getClass().getName() + "|" + modelLocation
            );

            GeckoHumanoidMeshAdapter.Result result = RuntimeMeshOwnership.callOwned(
                    () -> GeckoHumanoidMeshAdapter.analyseAndBake(
                            bakedModel, key, GeckoBindPoseTransforms.OPERATIONS)
            );
            if (!result.supported() || result.descriptor() == null || result.mesh() == null) {
                LOGGER.debug("EFPI Gecko humanoid rejected {}: {}", entityId, result.reason());
                return Inspection.rejected();
            }

            HumanoidSkeletonDescriptor descriptor = result.descriptor();
            ResourceLocation runtimeMeshId = new ResourceLocation(
                    EpicFightPostureIntegration.MODID,
                    "runtime_gecko/" + entityId.getNamespace() + "/" + entityId.getPath()
            );
            AssetAccessor<SkinnedMesh> mesh = SelfAccessor.create(runtimeMeshId, result.mesh());

            LOGGER.debug(
                    "EFPI Gecko humanoid accepted {} via shared pipeline: height={}, shoulderWidth={}, virtualJoints={}, confidence={}",
                    entityId,
                    descriptor.metrics().totalHeight(),
                    descriptor.metrics().shoulderWidth(),
                    descriptor.quality().virtualJointCount(),
                    descriptor.quality().overallConfidence()
            );

            return Inspection.supported(new PreparedHumanoidModel(
                    id(),
                    entityType,
                    entityId,
                    mesh,
                    Optional.of(descriptor)
            ));
        } catch (Throwable throwable) {
            LOGGER.debug(
                    "EFPI Gecko humanoid inspection failed for {}; skipping automatic compatibility",
                    entityId,
                    throwable
            );
            return Inspection.rejected();
        }
    }

    /**
     * Automatic startup discovery has no live entity instance. A Gecko model is
     * therefore eligible for static inspection only when its model resource can
     * be resolved safely with a null animatable. Entity-dependent models are
     * deliberately skipped instead of constructing a fake entity or guessing a
     * resource path.
     */
    private static ResourceLocation resolveStaticModelLocation(
            GeoModel geoModel,
            ResourceLocation entityId) {
        try {
            ResourceLocation modelLocation = geoModel.getModelResource(null);
            if (modelLocation == null) {
                LOGGER.debug(
                        "EFPI Gecko humanoid skipped {} because {} returned no model resource without a live entity",
                        entityId,
                        geoModel.getClass().getName()
                );
            }
            return modelLocation;
        } catch (Throwable throwable) {
            LOGGER.debug(
                    "EFPI Gecko humanoid skipped {} because {} requires a live entity to resolve its model resource ({})",
                    entityId,
                    geoModel.getClass().getName(),
                    throwable.getClass().getSimpleName()
            );
            return null;
        }
    }

    @Override
    public void addPatchedRenderer(PatchedRenderersEvent.Add event, PreparedHumanoidModel prepared) {
        event.addPatchedEntityRenderer(
                prepared.entityType(),
                type -> new SpecialStateGeckoRenderer(
                        prepared.mesh(),
                        prepared.skeleton().orElse(null),
                        event.getContext()
                )
        );
    }

    private GeckoHumanoidClientCompat() {
    }
}
