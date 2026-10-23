package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.client.ModelPartAttachmentPlan;
import dev.nire9734.epicfightposture.compat.humanoid.client.ModelPartAttachmentRenderCore;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.model.Armature;

/** NeoForge source-model preparation around the shared attachment renderer. */
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
            renderPrepared(plan, entity, renderer, buffer, poseStack,
                    packedLight, overlay, texture, armature);
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
        ModelPartAttachmentRenderCore.renderPrepared(plan, entity, renderer, buffer,
                poseStack, packedLight, overlay, texture, armature);
    }

    private ModelPartDynamicAttachmentRenderer() {
    }
}
