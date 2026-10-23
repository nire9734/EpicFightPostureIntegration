package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;

import java.util.List;

/** Common source-layer playback; loaders provide only preparation, layer access and scoped hooks. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class LivingRenderLayerReplay {
    public static void render(LivingEntity entity, EntityModel model,
                              List<RenderLayer<?, ?>> layers,
                              MultiBufferSource buffer, PoseStack poseStack,
                              int packedLight, float partialTicks, Armature armature,
                              SkinnedMesh primaryMesh,
                              HumanoidSkeletonDescriptor primaryDescriptor) {
        if (model == null || layers == null || layers.isEmpty() || armature == null) return;

        float limbSwingAmount = entity.isAlive() && !entity.isPassenger()
                ? Math.min(1.0F, entity.walkAnimation.speed(partialTicks)) : 0.0F;
        float limbSwing = entity.isAlive() && !entity.isPassenger()
                ? entity.walkAnimation.position(partialTicks) : 0.0F;
        if (entity.isBaby()) limbSwing *= 3.0F;

        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        float ageInTicks = entity.tickCount + partialTicks;

        poseStack.pushPose();
        try {
            MathUtils.mulStack(poseStack, armature.getPoseMatrices()[armature.rootJoint.getId()]);
            poseStack.scale(-1.0F, -1.0F, 1.0F);

            try (LionfishLayerRenderTargetCapture.Scope ignoredRenderTargets =
                         LionfishLayerRenderTargetCapture.open(buffer);
                 LionfishPrimaryModelLayerBridge.Scope ignoredPrimary =
                         LionfishPrimaryModelLayerBridge.open(model, primaryMesh, armature);
                 LionfishSecondaryModelLayerBridge.Scope ignoredSecondary =
                         LionfishSecondaryModelLayerBridge.open(model, primaryDescriptor, armature);
                 LayerPoseOverrideContext.Scope ignoredParts =
                         LayerPoseOverrideContext.open(entity, model, layers, armature, primaryDescriptor)) {
                for (RenderLayer layer : layers) {
                    if (layer == null) continue;
                    layer.render(poseStack, buffer, packedLight, entity,
                            limbSwing, limbSwingAmount, partialTicks, ageInTicks,
                            netHeadYaw, headPitch);
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    private LivingRenderLayerReplay() { }
}
