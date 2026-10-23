package dev.nire9734.epicfightposture.neoforge.compat.models;

import dev.nire9734.epicfightposture.compat.humanoid.client.ModelPartAttachmentPlan;
import dev.nire9734.epicfightposture.compat.humanoid.client.LayerPoseOverrideContext;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.neoforge.mixin.NeoForgeLivingEntityRendererLayersAccessor;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;

import java.util.List;

/**
 * Draws original ModelPart renderer-owned layers after the EFPI skinned body.
 * Uses exactly one saved/animated source-model frame for both accessory meshes
 * and layer passes. No Lionfish/GeckoLib classes are resolved in this path.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class NeoForgeModelPartLayerBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void render(
            LivingEntity entity, EntityRenderer<LivingEntity> source,
            MultiBufferSource buffer, PoseStack pose, int packedLight, int overlay,
            float partialTicks, ResourceLocation texture, Armature armature,
            HumanoidSkeletonDescriptor descriptor, ModelPartAttachmentPlan attachments) {
        if (!(source instanceof LivingEntityRenderer living)
                || !(living.getModel() instanceof HierarchicalModel<?>)) return;

        List<RenderLayer<?, ?>> layers = source instanceof NeoForgeLivingEntityRendererLayersAccessor accessor
                ? accessor.efpi$getLayers() : List.of();
        boolean hasAttachments = attachments != null && !attachments.isEmpty();
        boolean hasLayers = layers != null && !layers.isEmpty();
        if (!hasAttachments && !hasLayers) return;

        try (NeoForgeSourceModelPreparation.Scope prepared =
                     NeoForgeSourceModelPreparation.open(entity, source, partialTicks)) {
            if (!prepared.active()) return;

            if (hasAttachments) {
                ModelPartDynamicAttachmentRenderer.renderPrepared(attachments, entity, source,
                        buffer, pose, packedLight, overlay, texture, armature);
            }
            if (hasLayers) {
                replayLayers(entity, living, layers, buffer, pose, packedLight,
                        partialTicks, armature, descriptor);
            }
        }
    }

    private static void replayLayers(
            LivingEntity entity, LivingEntityRenderer renderer,
            List<RenderLayer<?, ?>> layers, MultiBufferSource buffer,
            PoseStack stack, int packedLight, float partialTicks,
            Armature armature, HumanoidSkeletonDescriptor descriptor) {
        float limbAmount = entity.isAlive() && !entity.isPassenger()
                ? Math.min(1.0F, entity.walkAnimation.speed(partialTicks)) : 0.0F;
        float limb = entity.isAlive() && !entity.isPassenger()
                ? entity.walkAnimation.position(partialTicks) : 0.0F;
        if (entity.isBaby()) limb *= 3.0F;
        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

        stack.pushPose();
        try (NeoForgeSpecialRenderHooks.Scope active = NeoForgeSpecialRenderHooks.beginLayerReplay();
             LayerPoseOverrideContext.Scope parts =
                     LayerPoseOverrideContext.open(entity, renderer.getModel(), layers,
                             armature, descriptor)) {
            MathUtils.mulStack(stack, armature.getPoseMatrices()[armature.rootJoint.getId()]);
            stack.scale(-1.0F, -1.0F, 1.0F);
            for (RenderLayer layer : layers) {
                if (layer == null) continue;
                try {
                    layer.render(stack, buffer, packedLight, entity, limb, limbAmount,
                            partialTicks, entity.tickCount + partialTicks,
                            Mth.wrapDegrees(headYaw - bodyYaw), headPitch);
                } catch (RuntimeException | LinkageError failure) {
                    LOGGER.debug("EFPI NeoForge skipped unsupported ModelPart layer {} for {}",
                            layer.getClass().getName(), entity.getType(), failure);
                }
            }
        } finally {
            stack.popPose();
        }
    }

    private NeoForgeModelPartLayerBridge() {}
}
