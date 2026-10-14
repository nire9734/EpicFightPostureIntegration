package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.mixin.client.LivingEntityRendererLayersAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;

import java.util.ArrayList;
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
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class LivingRenderLayerBridge {
    public static void replay(
            LivingEntity entity,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks,
            Armature armature) {
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)
                || !(livingRenderer instanceof LivingEntityRendererLayersAccessor accessor)
                || armature == null) {
            return;
        }

        List<RenderLayer<?, ?>> layers = accessor.efpi$getLayers();
        if (layers == null || layers.isEmpty()) {
            return;
        }

        EntityModel model = livingRenderer.getModel();
        List<ModelPartState> savedParts = saveModelParts(model);

        try {
            prepareSourceModel(entity, model, partialTicks);

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

            // Keep the old generic fallback basis. Exact per-part overrides are
            // expressed underneath it; if a source model cannot be normalized,
            // its layer behaves exactly as it did before this bridge extension.
            poseStack.pushPose();
            try {
                MathUtils.mulStack(poseStack, armature.getPoseMatrices()[armature.rootJoint.getId()]);
                poseStack.scale(-1.0F, -1.0F, 1.0F);

                for (RenderLayer layer : layers) {
                    if (layer == null) {
                        continue;
                    }
                    try (LayerPoseOverrideContext.Scope ignored =
                                 LayerPoseOverrideContext.open(entity, model, layer, armature)) {
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
            restoreModelParts(savedParts);
        }
    }

    private static void prepareSourceModel(LivingEntity entity, EntityModel model, float partialTicks) {
        if (model == null) {
            return;
        }

        if (model instanceof HierarchicalModel<?> hierarchical) {
            hierarchical.root().getAllParts().forEach(ModelPart::resetPose);
        }

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

        model.attackTime = entity.getAttackAnim(partialTicks);
        model.riding = entity.isPassenger();
        model.young = entity.isBaby();
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    private static List<ModelPartState> saveModelParts(EntityModel<?> model) {
        if (!(model instanceof HierarchicalModel<?> hierarchical)) {
            return List.of();
        }
        List<ModelPartState> saved = new ArrayList<>();
        hierarchical.root().getAllParts().forEach(part -> saved.add(new ModelPartState(
                part,
                part.storePose(),
                part.visible,
                part.skipDraw
        )));
        return saved;
    }

    private static void restoreModelParts(List<ModelPartState> saved) {
        for (ModelPartState state : saved) {
            state.part().loadPose(state.pose());
            state.part().visible = state.visible();
            state.part().skipDraw = state.skipDraw();
        }
    }

    private record ModelPartState(
            ModelPart part,
            net.minecraft.client.model.geom.PartPose pose,
            boolean visible,
            boolean skipDraw) {
    }

    private LivingRenderLayerBridge() {
    }
}
