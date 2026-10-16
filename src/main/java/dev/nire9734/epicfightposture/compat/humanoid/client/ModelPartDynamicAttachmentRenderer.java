package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.model.Armature;

import java.util.ArrayList;
import java.util.List;

/** Draws ModelPart attachment-node meshes while preserving source runtime visibility. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class ModelPartDynamicAttachmentRenderer {
    public static void render(
            ModelPartAttachmentPlan plan,
            LivingEntity entity,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            int overlay,
            float partialTicks,
            ResourceLocation texture,
            Armature armature) {
        if (plan == null || plan.isEmpty()
                || !(renderer instanceof LivingEntityRenderer livingRenderer)
                || !(livingRenderer.getModel() instanceof HierarchicalModel hierarchical)) {
            return;
        }

        List<PartState> saved = save(hierarchical);
        try {
            prepare(entity, (EntityModel) hierarchical, partialTicks);
            ModelPart root = hierarchical.root();
            RenderType renderType = RenderType.entityCutoutNoCull(texture);
            var poseMatrices = armature.getPoseMatrices();

            for (ModelPartAttachmentPlan.Entry entry : plan.entries()) {
                if (!isOwnGeometryVisible(root, entry.path())) {
                    continue;
                }
                entry.mesh().draw(
                        poseStack,
                        buffer,
                        renderType,
                        packedLight,
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F,
                        overlay,
                        armature,
                        poseMatrices
                );
            }
        } finally {
            restore(saved);
        }
    }

    private static boolean isOwnGeometryVisible(ModelPart root, List<String> path) {
        ModelPart cursor = root;
        if (!cursor.visible) {
            return false;
        }
        for (String segment : path) {
            try {
                cursor = cursor.getChild(segment);
            } catch (RuntimeException missing) {
                return false;
            }
            if (!cursor.visible) {
                return false;
            }
        }
        return !cursor.skipDraw;
    }

    private static void prepare(LivingEntity entity, EntityModel model, float partialTicks) {
        if (model instanceof HierarchicalModel<?> hierarchical) {
            hierarchical.root().getAllParts().forEach(ModelPart::resetPose);
        }

        boolean shouldSit = entity.isPassenger()
                && entity.getVehicle() != null
                && entity.getVehicle().shouldRiderSit();
        model.riding = shouldSit;
        model.young = entity.isBaby();

        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = headYaw - bodyYaw;
        if (shouldSit && entity.getVehicle() instanceof LivingEntity vehicle) {
            bodyYaw = Mth.rotLerp(partialTicks, vehicle.yBodyRotO, vehicle.yBodyRot);
            netHeadYaw = headYaw - bodyYaw;
            float wrapped = Mth.clamp(Mth.wrapDegrees(netHeadYaw), -85.0F, 85.0F);
            bodyYaw = headYaw - wrapped;
            if (wrapped * wrapped > 2500.0F) {
                bodyYaw += wrapped * 0.2F;
            }
            netHeadYaw = headYaw - bodyYaw;
        }

        float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
            headPitch *= -1.0F;
            netHeadYaw *= -1.0F;
        }

        float limbSwingAmount = 0.0F;
        float limbSwing = 0.0F;
        if (!shouldSit && entity.isAlive()) {
            limbSwingAmount = Math.min(1.0F, entity.walkAnimation.speed(partialTicks));
            limbSwing = entity.walkAnimation.position() - entity.walkAnimation.speed() * (1.0F - partialTicks);
            if (entity.isBaby()) {
                limbSwing *= 3.0F;
            }
        }

        float ageInTicks = entity.tickCount + partialTicks;
        model.attackTime = entity.getAttackAnim(partialTicks);
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    private static List<PartState> save(HierarchicalModel<?> model) {
        List<PartState> saved = new ArrayList<>();
        model.root().getAllParts().forEach(part -> saved.add(new PartState(
                part,
                part.storePose(),
                part.visible,
                part.skipDraw
        )));
        return saved;
    }

    private static void restore(List<PartState> saved) {
        for (PartState state : saved) {
            state.part().loadPose(state.pose());
            state.part().visible = state.visible();
            state.part().skipDraw = state.skipDraw();
        }
    }

    private record PartState(ModelPart part, PartPose pose, boolean visible, boolean skipDraw) {
    }

    private ModelPartDynamicAttachmentRenderer() {
    }
}
