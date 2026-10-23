package dev.nire9734.epicfightposture.neoforge.compat.models;

import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishLayerRenderTargetCapture;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishPrimaryModelLayerBridge;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishSecondaryModelLayerBridge;
import dev.nire9734.epicfightposture.compat.humanoid.client.LayerPoseOverrideContext;
import dev.nire9734.epicfightposture.compat.humanoid.client.LivingRenderLayerReplay;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.neoforge.mixin.NeoForgeLivingEntityRendererLayersAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;

import java.util.List;

/**
 * Replays visual passes owned by a source LivingEntityRenderer after EFPI draws
 * the retargeted body mesh.
 *
 * <p>Many modded mobs put translucent body passes, emissive details, held items,
 * shields, riders, and other presentation geometry in RenderLayer rather than in
 * the base model pass. Cancelling the source renderer without replaying these
 * layers therefore loses those details. During replay, LayerPoseOverrideContext
 * projects supported source model parts onto the same Epic Fight pose used by the
 * runtime mesh. Unsupported layers retain the historical Root-only fallback.</p>
 *
 * <p>Source-model animation preparation is shared with dynamic attachment replay
 * by {@link NeoForgeSourceModelPreparation}, avoiding a second reset/prepareMobModel/setupAnim
 * pass for the same entity in one special-state frame.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class NeoForgeLivingRenderLayerBridge {
    public static boolean hasReplayLayers(EntityRenderer<LivingEntity> renderer) {
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)
                || !(livingRenderer instanceof NeoForgeLivingEntityRendererLayersAccessor accessor)) {
            return false;
        }
        List<RenderLayer<?, ?>> layers = accessor.efpi$getLayers();
        return layers != null && !layers.isEmpty();
    }

    public static void replay(
            LivingEntity entity,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks,
            Armature armature,
            SkinnedMesh primaryMesh,
            HumanoidSkeletonDescriptor primaryDescriptor) {
        if (!hasReplayLayers(renderer)) {
            return;
        }
        try (NeoForgeSourceModelPreparation.Scope prepared =
                     NeoForgeSourceModelPreparation.open(entity, renderer, partialTicks)) {
            // Do not replay layers from an unprepared or unrestorable model.
            if (!prepared.active()) return;
            replayPrepared(
                    entity,
                    renderer,
                    buffer,
                    poseStack,
                    packedLight,
                    partialTicks,
                    armature,
                    primaryMesh,
                    primaryDescriptor
            );
        }
    }

    static void replayPrepared(
            LivingEntity entity,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks,
            Armature armature,
            SkinnedMesh primaryMesh,
            HumanoidSkeletonDescriptor primaryDescriptor) {
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)
                || !(livingRenderer instanceof NeoForgeLivingEntityRendererLayersAccessor accessor)
                || armature == null) {
            return;
        }

        List<RenderLayer<?, ?>> layers = accessor.efpi$getLayers();
        if (layers == null || layers.isEmpty()) {
            return;
        }

        EntityModel model = livingRenderer.getModel();
        try (NeoForgeSpecialRenderHooks.Scope ignoredLayer = NeoForgeSpecialRenderHooks.beginLayerReplay()) {
            LivingRenderLayerReplay.render(entity, model, layers, buffer, poseStack,
                    packedLight, partialTicks, armature, primaryMesh, primaryDescriptor);
        }
    }

    private NeoForgeLivingRenderLayerBridge() {
    }
}
