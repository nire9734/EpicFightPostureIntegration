package dev.nire9734.epicfightposture.compat.spider.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.spider.LightweightSpiderPatch;
import dev.nire9734.epicfightposture.mixin.client.LivingEntityRendererScaleInvoker;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.mesh.SpiderMesh;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

/**
 * Epic Fight special-state renderer for auto-detected Minecraft SpiderModel mobs.
 * Normal gameplay continues to use the source mod's renderer.
 */
public final class SpecialStateSpiderRenderer extends PatchedLivingEntityRenderer<
        LivingEntity,
        LightweightSpiderPatch,
        SpiderModel<LivingEntity>,
        LivingEntityRenderer<LivingEntity, SpiderModel<LivingEntity>>,
        SpiderMesh> {

    private LivingEntityRenderer<LivingEntity, SpiderModel<LivingEntity>> activeSourceRenderer;

    public SpecialStateSpiderRenderer(EntityRendererProvider.Context context, EntityType<?> entityType) {
        super(context, entityType);
    }

    @Override
    public void render(
            LivingEntity entity,
            LightweightSpiderPatch entitypatch,
            LivingEntityRenderer<LivingEntity, SpiderModel<LivingEntity>> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks) {
        this.activeSourceRenderer = renderer;
        try {
            super.render(entity, entitypatch, renderer, buffer, poseStack, packedLight, partialTicks);
        } finally {
            this.activeSourceRenderer = null;
        }
    }

    @Override
    public void mulPoseStack(
            PoseStack poseStack,
            Armature armature,
            LivingEntity entity,
            LightweightSpiderPatch entitypatch,
            float partialTicks) {
        super.mulPoseStack(poseStack, armature, entity, entitypatch, partialTicks);

        if (this.activeSourceRenderer != null) {
            ((LivingEntityRendererScaleInvoker) (Object) this.activeSourceRenderer)
                    .efpi$invokeScale(entity, poseStack, partialTicks);
        }
    }

    @Override
    public AssetAccessor<SpiderMesh> getDefaultMesh() {
        return Meshes.SPIDER;
    }
}
