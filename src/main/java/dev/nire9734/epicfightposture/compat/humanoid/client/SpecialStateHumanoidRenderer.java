package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.LightweightHumanoidPatch;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.SelfAccessor;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.HumanoidModelBaker;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedElytraLayer;
import yesman.epicfight.client.renderer.patched.layer.PatchedHeadLayer;
import yesman.epicfight.client.renderer.patched.layer.PatchedItemInHandLayer;
import yesman.epicfight.client.renderer.patched.layer.WearableItemLayer;

/**
 * Epic Fight renderer used only while {@link LightweightHumanoidPatch#overrideRender()}
 * is true.
 *
 * <p>The base mesh is baked lazily from the third-party renderer's actual
 * HumanoidModel using Epic Fight's own VanillaModelTransformer. This keeps the
 * source model's cubes, pivots and UVs, but converts them to an EF skinned mesh
 * so BIPED Root/Chest/Arm/Elbow/Knee animations can drive it directly.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class SpecialStateHumanoidRenderer extends PatchedLivingEntityRenderer<
        LivingEntity,
        LightweightHumanoidPatch,
        HumanoidModel<LivingEntity>,
        LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>>,
        SkinnedMesh> {

    private final ResourceLocation runtimeMeshId;
    private AssetAccessor<SkinnedMesh> mesh;
    private boolean bakeAttempted;

    public SpecialStateHumanoidRenderer(EntityRendererProvider.Context context, EntityType<?> entityType) {
        super(context, entityType);
        ResourceLocation entityId = EntityType.getKey(entityType);
        this.runtimeMeshId = new ResourceLocation(
                EpicFightPostureIntegration.MODID,
                "runtime_humanoid/" + entityId.getNamespace() + "/" + entityId.getPath()
        );

        // Safe fallback if a listed entity unexpectedly stops using HumanoidModel.
        this.mesh = (AssetAccessor<SkinnedMesh>) (AssetAccessor<?>) Meshes.BIPED;

        // Keep the common humanoid equipment layers attached to EF joints while the
        // special renderer is active. Unrecognized source-mod layers are handled by
        // initLayerLast() as original-model layers.
        this.addPatchedLayer(ElytraLayer.class, new PatchedElytraLayer<>());
        this.addPatchedLayer(ItemInHandLayer.class, new PatchedItemInHandLayer<>());
        this.addPatchedLayer(
                HumanoidArmorLayer.class,
                (yesman.epicfight.client.renderer.patched.layer.PatchedLayer) new WearableItemLayer(
                        Meshes.BIPED,
                        false,
                        context.getModelManager()
                )
        );
        this.addPatchedLayer(CustomHeadLayer.class, new PatchedHeadLayer<>());
    }

    @Override
    public void render(
            LivingEntity entity,
            LightweightHumanoidPatch entitypatch,
            LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks) {
        if (!this.bakeAttempted) {
            this.bakeAttempted = true;
            try {
                if (renderer.getModel() instanceof HumanoidModel<?> humanoidModel) {
                    SkinnedMesh baked = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(humanoidModel);
                    if (baked != null) {
                        this.mesh = SelfAccessor.create(this.runtimeMeshId, baked);
                    }
                }
            } catch (Throwable throwable) {
                EpicFightPostureIntegration.LOGGER.warn(
                        "Failed to bake EF special-state HumanoidModel mesh for {}. Falling back to Epic Fight BIPED mesh.",
                        EntityType.getKey(entity.getType()),
                        throwable
                );
            }
        }

        super.render(entity, entitypatch, renderer, buffer, poseStack, packedLight, partialTicks);
    }

    @Override
    public AssetAccessor<SkinnedMesh> getDefaultMesh() {
        return this.mesh;
    }

    @Override
    public void setJointTransforms(
            LightweightHumanoidPatch entitypatch,
            Armature armature,
            Pose pose,
            float partialTicks) {
        if (entitypatch.getOriginal().isBaby()) {
            pose.orElseEmpty("Head").frontResult(
                    JointTransform.scale(new Vec3f(1.25F, 1.25F, 1.25F)),
                    OpenMatrix4f::mul
            );
        }
    }

    @Override
    protected float getDefaultLayerHeightCorrection() {
        return 0.75F;
    }
}
