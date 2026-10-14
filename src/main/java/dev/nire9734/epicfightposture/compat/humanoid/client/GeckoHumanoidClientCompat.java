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
            ResourceLocation modelLocation = geoModel.getModelResource(null);
            BakedGeoModel bakedModel = geoModel.getBakedModel(modelLocation);
            SkeletonKey key = new SkeletonKey(
                    entityId,
                    geoModel.getClass().getName() + "|" + String.valueOf(modelLocation)
            );

            GeckoHumanoidMeshAdapter.Result result =
                    GeckoHumanoidMeshAdapter.analyseAndBake(bakedModel, key);
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
                    "EFPI Gecko humanoid inspection failed for {}: {}",
                    entityId,
                    throwable.toString()
            );
            return Inspection.rejected();
        }
    }

    @Override
    public void addPatchedRenderer(PatchedRenderersEvent.Add event, PreparedHumanoidModel prepared) {
        event.addPatchedEntityRenderer(
                prepared.entityType(),
                type -> new SpecialStateGeckoRenderer(prepared.mesh(), event.getContext())
        );
    }

    private GeckoHumanoidClientCompat() {
    }
}
