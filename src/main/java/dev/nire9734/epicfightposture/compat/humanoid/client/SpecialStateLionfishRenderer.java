package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.mixin.client.LivingEntityRendererScaleInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Epic Fight special-state renderer for LivingEntityRenderer-backed humanoids. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class SpecialStateLionfishRenderer extends PatchedEntityRenderer<
        LivingEntity,
        LivingEntityPatch<LivingEntity>,
        EntityRenderer<LivingEntity>,
        SkinnedMesh> {

    private final AssetAccessor<SkinnedMesh> mesh;

    public SpecialStateLionfishRenderer(AssetAccessor<SkinnedMesh> mesh, EntityRendererProvider.Context context) {
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

        // Forge RenderLivingEvent.Pre fires before LivingEntityRenderer.scale().
        // Preserve renderer-owned presentation scale while keeping EF responsible
        // for the special-state body transform.
        if (renderer instanceof LivingEntityRenderer<?, ?> livingRenderer
                && livingRenderer instanceof LivingEntityRendererScaleInvoker invoker) {
            invoker.efpi$invokeScale(entity, poseStack, partialTicks);
        }

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

        // The source render is cancelled while Epic Fight overrideRender() is
        // active. Preserve renderer-owned translucent/emissive/held-item/rider
        // passes instead of silently dropping them with the original body pass.
        LivingRenderLayerBridge.replay(
                entity,
                renderer,
                buffer,
                poseStack,
                packedLight,
                partialTicks
        );

        poseStack.popPose();
    }

    @Override
    public AssetAccessor<SkinnedMesh> getDefaultMesh() {
        return this.mesh;
    }
}
