package dev.nire9734.epicfightposture.neoforge.compat.models;

import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoResolvedHumanoidRig;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoPostRenderSkinMatrix;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtil;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * One-shot matrix for renderer-specific attachments drawn after GeckoLib's
 * normal layer callbacks. Iron's Spells' humanoid cape is one such path.
 *
 * <p>Only the matching original GeoBone consumes the matrix; other renderers
 * and all ordinary-state draws retain GeckoLib's original transform.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class NeoForgeGeckoPostRenderMatrixOverride {
    private static final ThreadLocal<Entry> NEXT = new ThreadLocal<>();

    public static void clear() {
        NEXT.remove();
    }

    public static void armTorsoFromEpicFight(LivingEntity entity,
                                             LivingEntityPatch<?> patch,
                                             EntityRenderer<?> sourceRenderer) {
        clear();
        // Mirror Forge's generic post-super bridge. The override itself is
        // one-shot and consumed only for the exact verified source chest bone.
        if (!(sourceRenderer instanceof GeoEntityRenderer geoRenderer)
                || !(entity instanceof GeoAnimatable animatable)) {
            return;
        }

        try {
            GeoModel model = geoRenderer.getGeoModel();
            ResourceLocation modelLocation = model.getModelResource(animatable);
            BakedGeoModel baked = model.getBakedModel(modelLocation);
            if (baked == null || baked.topLevelBones() == null) return;

            GeckoResolvedHumanoidRig rig = GeckoResolvedHumanoidRig.resolve(
                    baked, GeckoResolvedHumanoidRig.keyFor(entity, model, modelLocation),
                    GeckoBindPoseTransforms.OPERATIONS);
            GeoBone chestSource = rig.sourceBone(HumanoidJoint.CHEST).orElse(null);
            if (chestSource == null) return;

            // The post-super cape callback expects the authored local bind
            // pose, not the mutable GeoBone Euler values used by layer replay.
            BoneSnapshot initial = chestSource.getInitialSnapshot();
            if (initial != null) {
                chestSource.setPosX(initial.getOffsetX());
                chestSource.setPosY(initial.getOffsetY());
                chestSource.setPosZ(initial.getOffsetZ());
                chestSource.setRotX(initial.getRotX());
                chestSource.setRotY(initial.getRotY());
                chestSource.setRotZ(initial.getRotZ());
                chestSource.setScaleX(initial.getScaleX());
                chestSource.setScaleY(initial.getScaleY());
                chestSource.setScaleZ(initial.getScaleZ());
            }

            PoseStack bindStack = new PoseStack();
            RenderUtil.prepMatrixForBone(bindStack, chestSource);
            Matrix4f localBind = new Matrix4f(bindStack.last().pose());

            Matrix4f finalMatrix = GeckoPostRenderSkinMatrix.compose(patch.getArmature(), localBind);
            if (finalMatrix != null) NEXT.set(new Entry(chestSource, finalMatrix));
        } catch (RuntimeException | LinkageError ignored) {
            // Optional addon model. A model change must never break rendering.
            clear();
        }
    }

    public static boolean applyIfPresent(PoseStack poseStack, GeoBone bone) {
        Entry pending = NEXT.get();
        if (pending == null || pending.bone() != bone) return false;
        NEXT.remove();
        poseStack.mulPose(pending.matrix());
        return true;
    }

    private record Entry(GeoBone bone, Matrix4f matrix) {
    }

    private NeoForgeGeckoPostRenderMatrixOverride() {
    }
}
