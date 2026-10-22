package dev.nire9734.epicfightposture.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeLightweightHumanoidPatch;
import dev.nire9734.epicfightposture.neoforge.mixin.NeoForgeLivingRendererScaleInvoker;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

/**
 * Uses Epic Fight's animation/skinning pipeline for confirmed special states.
 * Ordinary mob rendering is unchanged through overrideRender() gating.
 * Armor and third-party render layers are not ported at this stage.
 */
public final class NeoForgeSpecialHumanoidRenderer extends PatchedLivingEntityRenderer<
        Mob,
        NeoForgeLightweightHumanoidPatch<Mob>,
        HumanoidModel<Mob>,
        LivingEntityRenderer<Mob, HumanoidModel<Mob>>,
        SkinnedMesh> {
    private final AssetAccessor<SkinnedMesh> runtimeMesh;
    // The source LivingEntityRenderer owns the vanilla per-entity scale.
    // Keep this local to render() so renderer instances cannot retain stale
    // entities or source renderers across render passes.
    private LivingEntityRenderer<Mob, HumanoidModel<Mob>> activeSourceRenderer;

    public NeoForgeSpecialHumanoidRenderer(EntityRendererProvider.Context context,
                                           EntityType<?> entityType,
                                           AssetAccessor<SkinnedMesh> runtimeMesh) {
        super(context, entityType);
        this.runtimeMesh = runtimeMesh;
    }

    @Override
    public void render(Mob entity, NeoForgeLightweightHumanoidPatch<Mob> entitypatch,
                       LivingEntityRenderer<Mob, HumanoidModel<Mob>> source,
                       MultiBufferSource buffer, PoseStack stack, int packedLight, float partialTicks) {
        activeSourceRenderer = source;
        try {
            super.render(entity, entitypatch, source, buffer, stack, packedLight, partialTicks);
        } finally {
            activeSourceRenderer = null;
        }
    }

    @Override
    public void mulPoseStack(PoseStack stack, Armature armature, Mob entity,
                             NeoForgeLightweightHumanoidPatch<Mob> entitypatch, float partialTicks) {
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
    protected float getDefaultLayerHeightCorrection() {
        return 0.75F;
    }
}
