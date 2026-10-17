package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
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
    private final ModelPartAttachmentPlan modelPartAttachments;
    private final HumanoidSkeletonDescriptor skeleton;

    public SpecialStateLionfishRenderer(AssetAccessor<SkinnedMesh> mesh, EntityRendererProvider.Context context) {
        this(mesh, context, null, null);
    }

    public SpecialStateLionfishRenderer(
            AssetAccessor<SkinnedMesh> mesh,
            EntityRendererProvider.Context context,
            ModelPartAttachmentPlan modelPartAttachments) {
        this(mesh, context, modelPartAttachments, null);
    }

    public SpecialStateLionfishRenderer(
            AssetAccessor<SkinnedMesh> mesh,
            EntityRendererProvider.Context context,
            ModelPartAttachmentPlan modelPartAttachments,
            HumanoidSkeletonDescriptor skeleton) {
        this.mesh = mesh;
        this.modelPartAttachments = modelPartAttachments;
        this.skeleton = skeleton;
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
        try {
            this.mulPoseStack(poseStack, armature, entity, entitypatch, partialTicks);

            if (renderer instanceof LivingEntityRenderer<?, ?> livingRenderer
                    && livingRenderer instanceof LivingEntityRendererScaleInvoker invoker) {
                invoker.efpi$invokeScale(entity, poseStack, partialTicks);
            }

            this.setArmaturePose(entitypatch, armature, partialTicks);
            SkinnedMesh runtimeMesh = this.mesh.get();
            float alpha = entity.isInvisibleTo(minecraft.player) ? 0.15F : 1.0F;
            RenderType bodyRenderType = RenderType.entityCutoutNoCull(texture);

            runtimeMesh.draw(
                    poseStack,
                    buffer,
                    bodyRenderType,
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

            boolean hasAttachments = this.modelPartAttachments != null
                    && !this.modelPartAttachments.isEmpty();
            boolean hasLayers = LivingRenderLayerBridge.hasReplayLayers(renderer);
            if (hasAttachments || hasLayers) {
                try (SourceModelPreparation.Scope ignored =
                             SourceModelPreparation.open(entity, renderer, partialTicks)) {
                    if (hasAttachments) {
                        ModelPartDynamicAttachmentRenderer.renderPrepared(
                                this.modelPartAttachments,
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

                    if (hasLayers) {
                        LivingRenderLayerBridge.replayPrepared(
                                entity,
                                renderer,
                                buffer,
                                poseStack,
                                packedLight,
                                partialTicks,
                                armature,
                                runtimeMesh,
                                this.skeleton
                        );
                    }
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public AssetAccessor<SkinnedMesh> getDefaultMesh() {
        return this.mesh;
    }
}
