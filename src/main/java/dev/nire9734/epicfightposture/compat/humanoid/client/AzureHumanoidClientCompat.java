package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import mod.azure.azurelib.cache.object.BakedGeoModel;
import mod.azure.azurelib.model.GeoModel;
import mod.azure.azurelib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.SelfAccessor;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;

import java.util.Optional;

/** Optional AzureLib 1.x humanoid backend. Loaded only when AzureLib is present. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class AzureHumanoidClientCompat implements HumanoidRenderBackend {
    public static final AzureHumanoidClientCompat INSTANCE = new AzureHumanoidClientCompat();
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String id() {
        return "azurelib";
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

            AzureHumanoidMeshAdapter.Result result =
                    AzureHumanoidMeshAdapter.analyseAndBake(bakedModel, key);
            if (!result.supported() || result.descriptor() == null || result.mesh() == null) {
                LOGGER.debug("EFPI Azure humanoid rejected {}: {}", entityId, result.reason());
                return Inspection.rejected();
            }

            HumanoidSkeletonDescriptor descriptor = result.descriptor();
            ResourceLocation runtimeMeshId = new ResourceLocation(
                    EpicFightPostureIntegration.MODID,
                    "runtime_azure/" + entityId.getNamespace() + "/" + entityId.getPath()
            );
            AssetAccessor<SkinnedMesh> mesh = SelfAccessor.create(runtimeMeshId, result.mesh());

            LOGGER.debug(
                    "EFPI Azure humanoid accepted {}: height={}, shoulderWidth={}, virtualJoints={}, confidence={}",
                    entityId,
                    descriptor.metrics().totalHeight(),
                    descriptor.metrics().shoulderWidth(),
                    descriptor.quality().virtualJointCount(),
                    descriptor.quality().overallConfidence()
            );

            return Inspection.supported(new PreparedHumanoidModel(
                    id(), entityType, entityId, mesh, Optional.of(descriptor)
            ));
        } catch (Throwable throwable) {
            LOGGER.debug("EFPI Azure humanoid inspection failed for {}: {}", entityId, throwable.toString());
            return Inspection.rejected();
        }
    }

    @Override
    public void addPatchedRenderer(PatchedRenderersEvent.Add event, PreparedHumanoidModel prepared) {
        event.addPatchedEntityRenderer(
                prepared.entityType(),
                type -> new SpecialStateAzureRenderer(prepared.mesh(), event.getContext())
        );
    }

    private AzureHumanoidClientCompat() {
    }
}
