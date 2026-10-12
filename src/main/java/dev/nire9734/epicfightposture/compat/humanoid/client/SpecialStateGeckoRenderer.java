package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;
import yesman.epicfight.api.client.event.types.render.PrepareModelEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Special-state renderer for GeckoLib-backed humanoids.
 *
 * Epic Fight 20.14.17's PCustomEntityRenderer passes 0.15 alpha for entities
 * that are visible to the local player, which makes EFPI-converted Gecko models
 * look transparent. This renderer mirrors the same EF drawing path but uses the
 * expected visibility alpha: visible = 1.0, invisible-to-player = 0.15.
 */
public final class SpecialStateGeckoRenderer extends PatchedEntityRenderer<
        LivingEntity,
        LivingEntityPatch<LivingEntity>,
        EntityRenderer<LivingEntity>,
        SkinnedMesh> {

    private final AssetAccessor<SkinnedMesh> mesh;

    public SpecialStateGeckoRenderer(
            AssetAccessor<SkinnedMesh> mesh,
            EntityRendererProvider.Context context) {
        this.mesh = mesh;
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
        // Preserve PatchedEntityRenderer's name-tag handling.
        super.render(entity, entitypatch, renderer, buffer, poseStack, packedLight, partialTicks);

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
            SkinnedMesh runtimeMesh = this.mesh.get();
            PrepareModelEvent prepareModelEvent = new PrepareModelEvent(
                    this,
                    runtimeMesh,
                    entitypatch,
                    buffer,
                    poseStack,
                    packedLight,
                    partialTicks
            );

            if (!EpicFightClientEventHooks.Render.PREPARE_MODEL_TO_RENDER.post(prepareModelEvent).isCanceled()) {
                float alpha = entity.isInvisibleTo(mc.player) ? 0.15F : 1.0F;
                runtimeMesh.draw(
                        poseStack,
                        buffer,
                        renderType,
                        packedLight,
                        1.0F,
                        1.0F,
                        1.0F,
                        alpha,
                        getOverlayCoord(entity),
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

    private static int getOverlayCoord(LivingEntity entity) {
        return OverlayTexture.pack(0, OverlayTexture.v(entity.hurtTime > 5));
    }

    @Override
    public AssetAccessor<SkinnedMesh> getDefaultMesh() {
        return this.mesh;
    }
}
