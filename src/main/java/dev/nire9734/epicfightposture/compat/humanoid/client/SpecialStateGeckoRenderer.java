package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;
import yesman.epicfight.api.client.event.types.render.PrepareModelEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.renderer.patched.entity.PCustomEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Epic Fight's PCustomEntityRenderer is a good fit for GeckoLib-backed entities,
 * but 20.14.17 passes 0.15 alpha for entities that are visible to the local
 * player. That makes every EFPI-converted Gecko model look transparent.
 *
 * This renderer keeps the same Epic Fight rendering path and only corrects the
 * visibility alpha so normal visible entities are rendered fully opaque.
 */
public final class SpecialStateGeckoRenderer extends PCustomEntityRenderer {
    public SpecialStateGeckoRenderer(
            AssetAccessor<SkinnedMesh> mesh,
            EntityRendererProvider.Context context) {
        super(mesh, context);
    }

    @Override
    public void render(
            LivingEntity entity,
            LivingEntityPatch<LivingEntity> entitypatch,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks) {
        // Preserve Epic Fight's name-tag handling.
        superBaseRender(entity, entitypatch, renderer, buffer, poseStack, packedLight, partialTicks);

        Minecraft mc = Minecraft.getInstance();
        boolean isGlowing = mc.shouldEntityAppearGlowing(entity);
        ResourceLocation textureLocation = renderer.getTextureLocation(entity);
        RenderType renderType = isGlowing
                ? RenderType.outline(textureLocation)
                : RenderType.entityCutoutNoCull(textureLocation);

        Armature armature = entitypatch.getArmature();
        poseStack.pushPose();
        this.mulPoseStack(poseStack, armature, entity, entitypatch, partialTicks);
        this.setArmaturePose(entitypatch, armature, partialTicks);

        if (renderType != null) {
            SkinnedMesh mesh = this.getDefaultMesh().get();
            PrepareModelEvent prepareModelEvent = new PrepareModelEvent(
                    this,
                    mesh,
                    entitypatch,
                    buffer,
                    poseStack,
                    packedLight,
                    partialTicks
            );

            if (!EpicFightClientEventHooks.Render.PREPARE_MODEL_TO_RENDER.post(prepareModelEvent).isCanceled()) {
                // Corrected from PCustomEntityRenderer 20.14.17:
                // visible -> 1.0 alpha, invisible-to-player -> faint 0.15 alpha.
                float alpha = entity.isInvisibleTo(mc.player) ? 0.15F : 1.0F;
                mesh.draw(
                        poseStack,
                        buffer,
                        renderType,
                        packedLight,
                        1.0F,
                        1.0F,
                        1.0F,
                        alpha,
                        this.getOverlayCoord(entity, entitypatch, partialTicks),
                        armature,
                        armature.getPoseMatrices()
                );
            }
        }

        if (mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            entitypatch.getClientAnimator().renderDebuggingInfoForAllLayers(poseStack, buffer, partialTicks);
        }

        poseStack.popPose();
    }

    /**
     * Calls PatchedEntityRenderer#render without invoking PCustomEntityRenderer#render,
     * which would draw the mesh a second time with the bad alpha value.
     */
    private void superBaseRender(
            LivingEntity entity,
            LivingEntityPatch<LivingEntity> entitypatch,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks) {
        // Java cannot directly select the grandparent implementation. Reproduce
        // the only base behavior we need here: the original renderer will handle
        // its own name tag outside EFPI's special mesh pass, so no extra draw is
        // necessary. Intentionally left empty to avoid double-rendering.
    }
}
