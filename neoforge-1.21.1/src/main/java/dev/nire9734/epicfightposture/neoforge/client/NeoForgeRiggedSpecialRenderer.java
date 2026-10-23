package dev.nire9734.epicfightposture.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeLightweightHumanoidPatch;
import dev.nire9734.epicfightposture.neoforge.mixin.NeoForgeLivingRendererScaleInvoker;
import dev.nire9734.epicfightposture.compat.humanoid.client.ModelPartAttachmentPlan;
import dev.nire9734.epicfightposture.neoforge.compat.models.ModelPartDynamicAttachmentRenderer;
import dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeModelPartLayerBridge;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;

/**
 * Special-state renderer for adapted rigged models. Ordinary rendering stays
 * with the original mod renderer; compatible accessories and layers are replayed
 * over Epic Fight's final pose while the original renderer is suppressed.
 */
public final class NeoForgeRiggedSpecialRenderer extends PatchedEntityRenderer<
        LivingEntity, NeoForgeLightweightHumanoidPatch<LivingEntity>, EntityRenderer<LivingEntity>, SkinnedMesh> {
    private final AssetAccessor<SkinnedMesh> runtimeMesh;
    private final ModelPartAttachmentPlan attachments;
    private final HumanoidSkeletonDescriptor skeleton;
    private final String sourceBackend;

    public NeoForgeRiggedSpecialRenderer(NeoForgePreparedRig prepared) {
        this.runtimeMesh = prepared.mesh();
        this.attachments = prepared.attachments();
        this.skeleton = prepared.skeleton();
        this.sourceBackend = prepared.backend();
    }

    @Override
    public void render(LivingEntity entity, NeoForgeLightweightHumanoidPatch<LivingEntity> patch,
                       EntityRenderer<LivingEntity> source, MultiBufferSource buffers,
                       PoseStack stack, int packedLight, float partialTicks) {
        super.render(entity, patch, source, buffers, stack, packedLight, partialTicks);
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation texture = source.getTextureLocation(entity);
        if (texture == null) return;

        Armature armature = patch.getArmature();
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(entity.hurtTime > 5));
        stack.pushPose();
        try {
            this.mulPoseStack(stack, armature, entity, patch, partialTicks);
            if (source instanceof LivingEntityRenderer<?, ?> living) {
                ((NeoForgeLivingRendererScaleInvoker) (Object) living)
                        .efpi$invokeScale(entity, stack, partialTicks);
            }
            this.setArmaturePose(patch, armature, partialTicks);
            SkinnedMesh mesh = runtimeMesh.get();
            float alpha = entity.isInvisibleTo(minecraft.player) ? .15F : 1.0F;
            mesh.draw(stack, buffers, RenderType.entityCutoutNoCull(texture),
                    packedLight, 1, 1, 1, alpha, overlay, armature, armature.getPoseMatrices());
            if ("modelpart".equals(sourceBackend)) {
                NeoForgeModelPartLayerBridge.render(entity, source, buffers, stack,
                        packedLight, overlay, partialTicks, texture, armature, skeleton, attachments);
            } else if (attachments != null && !attachments.isEmpty()) {
                ModelPartDynamicAttachmentRenderer.render(attachments, entity, source,
                        buffers, stack, packedLight, overlay, partialTicks, texture, armature);
            }
            if ("geckolib".equals(sourceBackend)) {
                NeoForgeOptionalBackendBridge.renderGeckoAttachments(entity, patch, source,
                        buffers, stack, packedLight, overlay, partialTicks, skeleton);
                NeoForgeOptionalBackendBridge.armGeckoPostRenderTorso(entity, patch, source);
            } else if ("lionfish".equals(sourceBackend)) {
                NeoForgeOptionalBackendBridge.renderLionfishLayers(entity, source, buffers,
                        stack, packedLight, partialTicks, armature, mesh, skeleton);
            }
            if (minecraft.shouldEntityAppearGlowing(entity)) {
                mesh.draw(stack, buffers, RenderType.outline(texture),
                        packedLight, 1, 1, 1, 1, overlay, armature, armature.getPoseMatrices());
            }
        } finally {
            stack.popPose();
        }
    }

    @Override public AssetAccessor<SkinnedMesh> getDefaultMesh() { return runtimeMesh; }
}
