package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.client.FrameSpikeDiagnostics;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.mixin.client.LivingEntityRendererLayersAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;

import java.util.List;

/**
 * Replays visual passes owned by a source LivingEntityRenderer after EFPI draws
 * the retargeted body mesh.
 *
 * <p>Many modded mobs put translucent body passes, emissive details, held items,
 * shields, riders, and other presentation geometry in RenderLayer rather than in
 * the base model pass. Cancelling the source renderer without replaying these
 * layers therefore loses those details. During replay, LayerPoseOverrideContext
 * projects supported source model parts onto the same Epic Fight pose used by the
 * runtime mesh. Unsupported layers retain the historical Root-only fallback.</p>
 *
 * <p>Source-model animation preparation is shared with dynamic attachment replay
 * by {@link SourceModelPreparation}, avoiding a second reset/prepareMobModel/setupAnim
 * pass for the same entity in one special-state frame.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class LivingRenderLayerBridge {
    public static boolean hasReplayLayers(EntityRenderer<LivingEntity> renderer) {
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)
                || !(livingRenderer instanceof LivingEntityRendererLayersAccessor accessor)) {
            return false;
        }
        List<RenderLayer<?, ?>> layers = accessor.efpi$getLayers();
        return layers != null && !layers.isEmpty();
    }

    public static void replay(
            LivingEntity entity,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks,
            Armature armature,
            SkinnedMesh primaryMesh,
            HumanoidSkeletonDescriptor primaryDescriptor) {
        if (!hasReplayLayers(renderer)) {
            return;
        }
        try (SourceModelPreparation.Scope ignored =
                     SourceModelPreparation.open(entity, renderer, partialTicks)) {
            replayPrepared(
                    entity,
                    renderer,
                    buffer,
                    poseStack,
                    packedLight,
                    partialTicks,
                    armature,
                    primaryMesh,
                    primaryDescriptor
            );
        }
    }

    static void replayPrepared(
            LivingEntity entity,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks,
            Armature armature,
            SkinnedMesh primaryMesh,
            HumanoidSkeletonDescriptor primaryDescriptor) {
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)
                || !(livingRenderer instanceof LivingEntityRendererLayersAccessor accessor)
                || armature == null) {
            return;
        }

        List<RenderLayer<?, ?>> layers = accessor.efpi$getLayers();
        if (layers == null || layers.isEmpty()) {
            return;
        }

        long startedNs = FrameSpikeDiagnostics.startTimedSection();
        try {
            EntityModel model = livingRenderer.getModel();

            float limbSwingAmount = entity.isAlive() && !entity.isPassenger()
                    ? Math.min(1.0F, entity.walkAnimation.speed(partialTicks))
                    : 0.0F;
            float limbSwing = entity.isAlive() && !entity.isPassenger()
                    ? entity.walkAnimation.position(partialTicks)
                    : 0.0F;
            if (entity.isBaby()) {
                limbSwing *= 3.0F;
            }

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
                             LayerPoseOverrideContext.open(
                                     entity,
                                     model,
                                     layers,
                                     armature,
                                     primaryDescriptor)) {
                    for (RenderLayer layer : layers) {
                        if (layer == null) {
                            continue;
                        }
                        layer.render(
                                poseStack,
                                buffer,
                                packedLight,
                                entity,
                                limbSwing,
                                limbSwingAmount,
                                partialTicks,
                                ageInTicks,
                                netHeadYaw,
                                headPitch
                        );
                    }
                }
            } finally {
                poseStack.popPose();
            }
        } finally {
            FrameSpikeDiagnostics.finishLayerReplay(startedNs);
        }
    }

    private LivingRenderLayerBridge() {
    }
}
