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
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Special-state renderer for GeckoLib-backed humanoids.
 *
 * The normal textured pass is always rendered. If Minecraft marks the entity as
 * glowing (for example while it is targeted), an outline pass is rendered in
 * addition to the textured pass instead of replacing it.
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
        super.render(entity, entitypatch, renderer, buffer, poseStack, packedLight, partialTicks);

        Minecraft mc = Minecraft.getInstance();
        ResourceLocation textureLocation = renderer.getTextureLocation(entity);
        Armature armature = entitypatch.getArmature();
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(entity.hurtTime > 5));

        poseStack.pushPose();
        this.mulPoseStack(poseStack, armature, entity, entitypatch, partialTicks);
        this.setArmaturePose(entitypatch, armature, partialTicks);

        SkinnedMesh runtimeMesh = this.mesh.get();

        // Main colour/texture pass. This must not be replaced by the outline
        // render type when the entity is glowing.
        float baseAlpha = entity.isInvisibleTo(mc.player) ? 0.15F : 1.0F;
        runtimeMesh.draw(
                poseStack,
                buffer,
                RenderType.entityCutoutNoCull(textureLocation),
                packedLight,
                1.0F,
                1.0F,
                1.0F,
                baseAlpha,
                overlay,
                armature,
                armature.getPoseMatrices()
        );

        // Glowing is a second pass, just like vanilla's visible model + outline.
        if (mc.shouldEntityAppearGlowing(entity)) {
            runtimeMesh.draw(
                    poseStack,
                    buffer,
                    RenderType.outline(textureLocation),
                    packedLight,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    overlay,
                    armature,
                    armature.getPoseMatrices()
            );
        }

        poseStack.popPose();
    }

    @Override
    public AssetAccessor<SkinnedMesh> getDefaultMesh() {
        return this.mesh;
    }
}
