package dev.nire9734.epicfightposture.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeLightweightHumanoidPatch;
import dev.nire9734.epicfightposture.neoforge.mixin.NeoForgeLivingRendererScaleInvoker;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedElytraLayer;
import yesman.epicfight.client.renderer.patched.layer.PatchedHeadLayer;
import yesman.epicfight.client.renderer.patched.layer.PatchedItemInHandLayer;
import yesman.epicfight.client.renderer.patched.layer.WearableItemLayer;

/**
 * Uses Epic Fight's animation/skinning pipeline for confirmed special states.
 * Ordinary mob rendering is unchanged through overrideRender() gating.
 * Epic Fight's standard humanoid armor, head, item and elytra layers are
 * replayed during special-state rendering, as on Forge.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class NeoForgeSpecialHumanoidRenderer extends PatchedLivingEntityRenderer<
        LivingEntity,
        NeoForgeLightweightHumanoidPatch<LivingEntity>,
        HumanoidModel<LivingEntity>,
        LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>>,
        SkinnedMesh> {
    private final AssetAccessor<SkinnedMesh> runtimeMesh;
    // The source LivingEntityRenderer owns the vanilla per-entity scale.
    // Keep this local to render() so renderer instances cannot retain stale
    // entities or source renderers across render passes.
    private LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>> activeSourceRenderer;

    public NeoForgeSpecialHumanoidRenderer(EntityRendererProvider.Context context,
                                           EntityType<?> entityType,
                                           AssetAccessor<SkinnedMesh> runtimeMesh) {
        super(context, entityType);
        this.runtimeMesh = runtimeMesh;
        this.addPatchedLayer(ElytraLayer.class, new PatchedElytraLayer<>());
        this.addPatchedLayer(ItemInHandLayer.class, new PatchedItemInHandLayer<>());
        this.addPatchedLayer(HumanoidArmorLayer.class,
                (yesman.epicfight.client.renderer.patched.layer.PatchedLayer) new WearableItemLayer(
                        Meshes.BIPED, false, context.getModelManager()));
        this.addPatchedLayer(CustomHeadLayer.class, new PatchedHeadLayer<>());
    }

    @Override
    public void render(LivingEntity entity, NeoForgeLightweightHumanoidPatch<LivingEntity> entitypatch,
                       LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>> source,
                       MultiBufferSource buffer, PoseStack stack, int packedLight, float partialTicks) {
        activeSourceRenderer = source;
        try {
            super.render(entity, entitypatch, source, buffer, stack, packedLight, partialTicks);
        } finally {
            activeSourceRenderer = null;
        }
    }

    @Override
    public void mulPoseStack(PoseStack stack, Armature armature, LivingEntity entity,
                             NeoForgeLightweightHumanoidPatch<LivingEntity> entitypatch, float partialTicks) {
        super.mulPoseStack(stack, armature, entity, entitypatch, partialTicks);
        // Epic Fight's model transform does not include custom vanilla scale.
        // Dynamically invokes GiantMobRenderer.scale (or any subclass override)
        // using the renderer that would have drawn the entity normally.
        if (activeSourceRenderer != null) {
            ((NeoForgeLivingRendererScaleInvoker) (Object) activeSourceRenderer)
                    .efpi$invokeScale(entity, stack, partialTicks);
        }
    }

    @Override
    public AssetAccessor<SkinnedMesh> getDefaultMesh() {
        return runtimeMesh;
    }

    @Override
    public void setJointTransforms(NeoForgeLightweightHumanoidPatch<LivingEntity> patch,
                                   Armature armature, Pose pose, float partialTicks) {
        if (patch.getOriginal().isBaby()) {
            pose.orElseEmpty("Head").frontResult(
                    JointTransform.scale(new Vec3f(1.25F, 1.25F, 1.25F)),
                    OpenMatrix4f::mul);
        }
    }

    @Override
    protected float getDefaultLayerHeightCorrection() {
        return 0.75F;
    }
}
