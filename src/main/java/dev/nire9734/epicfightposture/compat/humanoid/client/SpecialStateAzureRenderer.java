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
 * Special-state body renderer for AzureLib-backed humanoids.
 *
 * <p>Epic Fight's AzureLib compatibility invokes patched rendering from
 * AzureLib's GeoRenderEvent.Entity.Pre and cancels the source Geo renderer while
 * overrideRender() is active. Therefore this renderer mirrors the Gecko render
 * lifecycle: it consumes the incoming Azure event PoseStack and must not replay
 * LivingEntityRenderer.scale() or another source-renderer presentation transform.</p>
 */
public final class SpecialStateAzureRenderer extends PatchedEntityRenderer<
        LivingEntity,
        LivingEntityPatch<LivingEntity>,
        EntityRenderer<LivingEntity>,
        SkinnedMesh> {

    private final AssetAccessor<SkinnedMesh> mesh;

    public SpecialStateAzureRenderer(
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

        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation texture = renderer.getTextureLocation(entity);
        Armature armature = entitypatch.getArmature();
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(entity.hurtTime > 5));

        poseStack.pushPose();
        this.mulPoseStack(poseStack, armature, entity, entitypatch, partialTicks);
        this.setArmaturePose(entitypatch, armature, partialTicks);

        SkinnedMesh runtimeMesh = this.mesh.get();
        float alpha = entity.isInvisibleTo(minecraft.player) ? 0.15F : 1.0F;
        runtimeMesh.draw(
                poseStack,
                buffer,
                RenderType.entityCutoutNoCull(texture),
                packedLight,
                1.0F, 1.0F, 1.0F, alpha,
                overlay,
                armature,
                armature.getPoseMatrices()
        );

        if (minecraft.shouldEntityAppearGlowing(entity)) {
            runtimeMesh.draw(
                    poseStack,
                    buffer,
                    RenderType.outline(texture),
                    packedLight,
                    1.0F, 1.0F, 1.0F, 1.0F,
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
