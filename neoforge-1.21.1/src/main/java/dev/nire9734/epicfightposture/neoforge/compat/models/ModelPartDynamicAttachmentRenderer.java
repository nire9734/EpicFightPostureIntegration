package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.model.Armature;

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
        if (plan == null || plan.isEmpty()) {
            return;
        }
        try (NeoForgeSourceModelPreparation.Scope ignored =
                     NeoForgeSourceModelPreparation.open(entity, renderer, partialTicks)) {
            renderPrepared(
                    plan,
                    entity,
                    renderer,
                    buffer,
                    poseStack,
                    packedLight,
                    overlay,
                    texture,
                    armature
            );
        }
    }

    public static void renderPrepared(
            ModelPartAttachmentPlan plan,
            LivingEntity entity,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            int overlay,
            ResourceLocation texture,
            Armature armature) {
        if (plan == null || plan.isEmpty()
                || !(renderer instanceof LivingEntityRenderer livingRenderer)
                || !(livingRenderer.getModel() instanceof HierarchicalModel hierarchical)) {
            return;
        }

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

    private ModelPartDynamicAttachmentRenderer() {
    }
}
