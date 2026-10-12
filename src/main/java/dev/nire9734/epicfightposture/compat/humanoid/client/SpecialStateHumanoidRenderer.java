package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.LightweightHumanoidPatch;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
 * Thin adapter that supplies an EF-owned runtime mesh for compatibility mobs.
 *
 * <p>All actual body rendering, armature posing, animation application and layer
 * dispatch are inherited from Epic Fight's {@link PatchedLivingEntityRenderer}.
 * EFPI only provides the runtime mesh and the standard humanoid layer mapping
 * that Epic Fight cannot infer for an arbitrary third-party entity type.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class SpecialStateHumanoidRenderer extends PatchedLivingEntityRenderer<
        LivingEntity,
        LightweightHumanoidPatch,
        HumanoidModel<LivingEntity>,
        LivingEntityRenderer<LivingEntity, HumanoidModel<LivingEntity>>,
        SkinnedMesh> {

    private final AssetAccessor<SkinnedMesh> mesh;

    public SpecialStateHumanoidRenderer(
            EntityRendererProvider.Context context,
            EntityType<?> entityType,
            AssetAccessor<SkinnedMesh> mesh) {
        super(context, entityType);
        this.mesh = mesh;

        // These are Epic Fight's own patched layers. Unknown source-mod layers are
        // intentionally not mirrored because some of them redraw the whole body.
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
