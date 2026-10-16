package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.SelfAccessor;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;

import java.util.Optional;

/** Lionfish/Citadel-style humanoid backend. */
public final class LionfishHumanoidClientCompat implements HumanoidRenderBackend {
    public static final LionfishHumanoidClientCompat INSTANCE = new LionfishHumanoidClientCompat();
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String id() {
        return "lionfish";
    }

    @Override
    public Inspection inspect(EntityType<?> entityType, EntityRenderer<?> renderer) {
        if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)
                || !(livingRenderer.getModel() instanceof AdvancedEntityModel<?> advancedModel)) {
            return Inspection.unmatched();
        }

        ResourceLocation entityId = EntityType.getKey(entityType);
        if (entityId == null) {
            return Inspection.rejected();
        }

        try {
            SkeletonKey key = new SkeletonKey(entityId.toString(), advancedModel.getClass().getName());
            LionfishHumanoidMeshAdapter.Result result = RuntimeMeshOwnership.callOwned(
                    () -> LionfishHumanoidMeshAdapter.analyseAndBake(advancedModel, key)
            );
            if (!result.supported() || result.descriptor() == null || result.mesh() == null) {
                LOGGER.debug("EFPI Lionfish humanoid resolver rejected {}: {}", entityId, result.reason());
                return Inspection.rejected();
            }

            HumanoidSkeletonDescriptor descriptor = result.descriptor();
            ResourceLocation runtimeMeshId = new ResourceLocation(
                    EpicFightPostureIntegration.MODID,
                    "runtime_lionfish/" + entityId.getNamespace() + "/" + entityId.getPath()
            );
            AssetAccessor<SkinnedMesh> mesh = SelfAccessor.create(runtimeMeshId, result.mesh());

            LOGGER.debug(
                    "EFPI Lionfish humanoid accepted {}: height={}, shoulderWidth={}, armL={}/{}, armR={}/{}, virtualJoints={}, confidence={}",
                    entityId,
                    descriptor.metrics().totalHeight(),
                    descriptor.metrics().shoulderWidth(),
                    descriptor.metrics().upperArmLeft(),
                    descriptor.metrics().lowerArmLeft(),
                    descriptor.metrics().upperArmRight(),
                    descriptor.metrics().lowerArmRight(),
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
            LOGGER.warn("EFPI could not inspect Lionfish humanoid model for {}; skipping automatic compatibility",
                    entityId, throwable);
            return Inspection.rejected();
        }
    }

    @Override
    public void addPatchedRenderer(PatchedRenderersEvent.Add event, PreparedHumanoidModel prepared) {
        HumanoidSkeletonDescriptor descriptor = prepared.skeleton().orElse(null);
        event.addPatchedEntityRenderer(
                prepared.entityType(),
                type -> new SpecialStateLionfishRenderer(
                        prepared.mesh(),
                        event.getContext(),
                        null,
                        descriptor)
        );
    }

    private LionfishHumanoidClientCompat() {
    }
}
