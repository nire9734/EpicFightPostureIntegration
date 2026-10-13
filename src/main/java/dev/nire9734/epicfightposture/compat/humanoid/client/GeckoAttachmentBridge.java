package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges GeckoLib attachment bones/render-layers onto the Epic Fight BIPED pose.
 *
 * <p>Epic Fight cancels GeckoLib's normal render from GeoRenderEvent.Pre while
 * overrideRender() is active. That means Gecko's actuallyRender() never updates
 * its bones and its GeoRenderLayers never run. A renderer that draws a cape or
 * other decoration after super.render() can therefore read a stale bone pose,
 * while BlockAndItemGeoLayer / ItemArmorGeoLayer disappear entirely.</p>
 *
 * <p>This bridge deliberately leaves the already-working EF skinned body mesh
 * alone. It resets the source GeoBone tree to the bind snapshot, applies the
 * current EF local JointTransform at semantic joint transitions, then replays
 * Gecko's per-bone render-layer callbacks on that synchronized hierarchy. The
 * synchronized bones are intentionally left in place for the remainder of the
 * source renderer call so post-super custom attachment rendering can use them.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class GeckoAttachmentBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Class<?>> WARNED_RENDERERS = ConcurrentHashMap.newKeySet();

    private static final int ROOT = 0;
    private static final int THIGH_R = 1;
    private static final int LEG_R = 2;
    private static final int KNEE_R = 3;
    private static final int THIGH_L = 4;
    private static final int LEG_L = 5;
    private static final int KNEE_L = 6;
    private static final int CHEST = 8;
    private static final int HEAD = 9;
    private static final int ARM_R = 11;
    private static final int HAND_R = 12;
    private static final int ELBOW_R = 14;
    private static final int ARM_L = 16;
    private static final int HAND_L = 17;
    private static final int ELBOW_L = 19;

    static void syncAndRenderLayers(
            LivingEntity entity,
            LivingEntityPatch<LivingEntity> entityPatch,
            EntityRenderer<LivingEntity> sourceRenderer,
            MultiBufferSource bufferSource,
            PoseStack poseStack,
            int packedLight,
            int packedOverlay,
            float partialTicks) {
        if (!(sourceRenderer instanceof GeoEntityRenderer geoRenderer)
                || !(entity instanceof GeoAnimatable animatable)) {
            return;
        }

        try {
            GeoModel geoModel = geoRenderer.getGeoModel();
            ResourceLocation modelLocation = geoModel.getModelResource(animatable);
            BakedGeoModel bakedModel = geoModel.getBakedModel(modelLocation);
            if (bakedModel == null || bakedModel.topLevelBones() == null) {
                return;
            }

            Armature armature = entityPatch.getArmature();
            Pose efPose = entityPatch.getAnimator().getPose(partialTicks);
            Map<GeoBone, Integer> bindings = new IdentityHashMap<>();

            for (GeoBone top : bakedModel.topLevelBones()) {
                syncBoneTree(top, -1, armature, efPose, bindings);
            }

            replayPerBoneLayers(
                    geoRenderer,
                    animatable,
                    bakedModel,
                    bindings,
                    bufferSource,
                    poseStack,
                    packedLight,
                    packedOverlay,
                    partialTicks
            );
        } catch (Throwable throwable) {
            if (WARNED_RENDERERS.add(sourceRenderer.getClass())) {
                LOGGER.warn(
                        "EFPI could not bridge Gecko attachment rendering for {} ({})",
                        sourceRenderer.getClass().getName(),
                        entity.getType(),
                        throwable
                );
            }
        }
    }

    private static void syncBoneTree(
            GeoBone bone,
            int inheritedJoint,
            Armature armature,
            Pose efPose,
            Map<GeoBone, Integer> bindings) {
        if (bone == null) {
            return;
        }

        resetToInitial(bone);

        Integer explicitJoint = classifyJoint(bone, inheritedJoint);
        int joint = explicitJoint != null
                ? explicitJoint
                : (inheritedJoint >= 0 ? inheritedJoint : ROOT);
        bindings.put(bone, joint);

        // Only apply an EF local transform when the Geo hierarchy actually
        // transitions to another semantic BIPED joint. Applying the same joint
        // transform to every unknown child would compound rotations.
        if (inheritedJoint < 0 || (explicitJoint != null && joint != inheritedJoint)) {
            applyJointAnimation(bone, armature, efPose, joint);
        }

        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                syncBoneTree(child, joint, armature, efPose, bindings);
            }
        }
    }

    private static void resetToInitial(GeoBone bone) {
        BoneSnapshot snapshot = bone.getInitialSnapshot();
        if (snapshot == null) {
            return;
        }

        bone.setPosX(snapshot.getOffsetX());
        bone.setPosY(snapshot.getOffsetY());
        bone.setPosZ(snapshot.getOffsetZ());
        bone.setRotX(snapshot.getRotX());
        bone.setRotY(snapshot.getRotY());
        bone.setRotZ(snapshot.getRotZ());
        bone.setScaleX(snapshot.getScaleX());
        bone.setScaleY(snapshot.getScaleY());
        bone.setScaleZ(snapshot.getScaleZ());
    }

    private static void applyJointAnimation(GeoBone bone, Armature armature, Pose pose, int jointId) {
        BoneSnapshot snapshot = bone.getInitialSnapshot();
        Joint joint = armature.searchJointById(jointId);
        if (snapshot == null || joint == null) {
            return;
        }

        JointTransform transform = pose.orElseEmpty(joint.getName());
        Vec3f translation = transform.translation();
        Vec3f scale = transform.scale();
        float[] rotation = quaternionToZyxEuler(transform.rotation());

        // Gecko's bone translation convention negates X in prepMatrixForBone,
        // while Y/Z retain their sign. EF animation translation is in model
        // units, Gecko bone offsets are in model pixels.
        bone.setPosX(snapshot.getOffsetX() - translation.x * 16.0F);
        bone.setPosY(snapshot.getOffsetY() + translation.y * 16.0F);
        bone.setPosZ(snapshot.getOffsetZ() + translation.z * 16.0F);

        bone.setRotX(snapshot.getRotX() + rotation[0]);
        bone.setRotY(snapshot.getRotY() + rotation[1]);
        bone.setRotZ(snapshot.getRotZ() + rotation[2]);

        bone.setScaleX(snapshot.getScaleX() * scale.x);
        bone.setScaleY(snapshot.getScaleY() * scale.y);
        bone.setScaleZ(snapshot.getScaleZ() * scale.z);
    }

    /**
     * Returns X/Y/Z angles for Rz(z) * Ry(y) * Rx(x), matching GeckoLib's
     * rotateMatrixAroundBone order.
     */
    private static float[] quaternionToZyxEuler(Quaternionf source) {
        Quaternionf q = new Quaternionf(source).normalize();
        float x = q.x;
        float y = q.y;
        float z = q.z;
        float w = q.w;

        float sinXCosY = 2.0F * (w * x + y * z);
        float cosXCosY = 1.0F - 2.0F * (x * x + y * y);
        float rotX = (float)Math.atan2(sinXCosY, cosXCosY);

        float sinY = 2.0F * (w * y - z * x);
        float rotY = Math.abs(sinY) >= 1.0F
                ? (float)Math.copySign(Math.PI / 2.0, sinY)
                : (float)Math.asin(sinY);

        float sinZCosY = 2.0F * (w * z + x * y);
        float cosZCosY = 1.0F - 2.0F * (y * y + z * z);
        float rotZ = (float)Math.atan2(sinZCosY, cosZCosY);

        return new float[] {rotX, rotY, rotZ};
    }

    private static void replayPerBoneLayers(
            GeoEntityRenderer geoRenderer,
            GeoAnimatable animatable,
            BakedGeoModel bakedModel,
            Map<GeoBone, Integer> bindings,
            MultiBufferSource bufferSource,
            PoseStack poseStack,
            int packedLight,
            int packedOverlay,
            float partialTicks) {
        List<GeoRenderLayer> layers = new ArrayList<>(geoRenderer.getRenderLayers());
        if (layers.isEmpty()) {
            return;
        }

        ResourceLocation texture = geoRenderer.getTextureLocation(animatable);
        RenderType renderType = geoRenderer.getRenderType(animatable, texture, bufferSource, partialTicks);
        VertexConsumer baseBuffer = renderType == null ? null : bufferSource.getBuffer(renderType);

        for (GeoRenderLayer layer : layers) {
            layer.preRender(
                    poseStack,
                    animatable,
                    bakedModel,
                    renderType,
                    bufferSource,
                    baseBuffer,
                    partialTicks,
                    packedLight,
                    packedOverlay
            );
        }

        if (renderType == null || baseBuffer == null) {
            return;
        }

        for (GeoBone top : bakedModel.topLevelBones()) {
            replayBoneLayers(
                    top,
                    layers,
                    animatable,
                    renderType,
                    bufferSource,
                    baseBuffer,
                    poseStack,
                    packedLight,
                    packedOverlay,
                    partialTicks
            );
        }
    }

    private static void replayBoneLayers(
            GeoBone bone,
            List<GeoRenderLayer> layers,
            GeoAnimatable animatable,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer baseBuffer,
            PoseStack poseStack,
            int packedLight,
            int packedOverlay,
            float partialTicks) {
        if (bone == null) {
            return;
        }

        poseStack.pushPose();
        RenderUtils.prepMatrixForBone(poseStack, bone);

        for (GeoRenderLayer layer : layers) {
            layer.renderForBone(
                    poseStack,
                    animatable,
                    bone,
                    renderType,
                    bufferSource,
                    baseBuffer,
                    partialTicks,
                    packedLight,
                    packedOverlay
            );
        }

        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                replayBoneLayers(
                        child,
                        layers,
                        animatable,
                        renderType,
                        bufferSource,
                        baseBuffer,
                        poseStack,
                        packedLight,
                        packedOverlay,
                        partialTicks
                );
            }
        }

        poseStack.popPose();
    }

    private static Integer classifyJoint(GeoBone bone, int inheritedJoint) {
        String name = normalize(bone.getName());
        if (name.isEmpty()) {
            return null;
        }

        if (isAny(name, "root", "master", "base", "main")) {
            return ROOT;
        }
        if (name.equals("body")) {
            return hasTorsoChild(bone) ? ROOT : CHEST;
        }
        if (isAny(name, "torso", "chest", "upperbody", "trunk", "spine")) {
            return CHEST;
        }
        if (containsAny(name, "head", "skull", "helmet", "hat")) {
            return HEAD;
        }

        if (isRight(name) && containsAny(name, "hand", "palm", "wrist", "item", "weapon", "held")) {
            return HAND_R;
        }
        if (isLeft(name) && containsAny(name, "hand", "palm", "wrist", "item", "weapon", "held")) {
            return HAND_L;
        }
        if (inheritedJoint == ARM_R && containsAny(name, "hand", "palm", "wrist", "item", "weapon", "held")) {
            return HAND_R;
        }
        if (inheritedJoint == ARM_L && containsAny(name, "hand", "palm", "wrist", "item", "weapon", "held")) {
            return HAND_L;
        }

        if (isRight(name) && containsAny(name, "elbow")) {
            return ELBOW_R;
        }
        if (isLeft(name) && containsAny(name, "elbow")) {
            return ELBOW_L;
        }
        if (isRight(name) && containsAny(name, "forearm", "lowerarm")) {
            return HAND_R;
        }
        if (isLeft(name) && containsAny(name, "forearm", "lowerarm")) {
            return HAND_L;
        }
        if (isRight(name) && containsAny(name, "shoulder", "upperarm", "arm")) {
            return ARM_R;
        }
        if (isLeft(name) && containsAny(name, "shoulder", "upperarm", "arm")) {
            return ARM_L;
        }

        if (isRight(name) && containsAny(name, "knee")) {
            return KNEE_R;
        }
        if (isLeft(name) && containsAny(name, "knee")) {
            return KNEE_L;
        }
        if (isRight(name) && containsAny(name, "foot", "boot", "shin", "calf", "lowerleg")) {
            return LEG_R;
        }
        if (isLeft(name) && containsAny(name, "foot", "boot", "shin", "calf", "lowerleg")) {
            return LEG_L;
        }
        if (inheritedJoint == THIGH_R && containsAny(name, "foot", "boot", "shin", "calf", "lowerleg")) {
            return LEG_R;
        }
        if (inheritedJoint == THIGH_L && containsAny(name, "foot", "boot", "shin", "calf", "lowerleg")) {
            return LEG_L;
        }
        if (isRight(name) && containsAny(name, "thigh", "upperleg", "leg")) {
            return THIGH_R;
        }
        if (isLeft(name) && containsAny(name, "thigh", "upperleg", "leg")) {
            return THIGH_L;
        }

        if (containsAny(name, "cape", "cloak", "mantle", "back", "backpack")) {
            return CHEST;
        }

        return null;
    }

    private static boolean hasTorsoChild(GeoBone bone) {
        for (GeoBone child : bone.getChildBones()) {
            String name = normalize(child.getName());
            if (isAny(name, "torso", "chest", "upperbody", "trunk", "spine")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRight(String name) {
        return name.contains("right")
                || name.startsWith("rarm")
                || name.startsWith("rhand")
                || name.startsWith("rleg")
                || name.startsWith("rfoot")
                || name.endsWith("r");
    }

    private static boolean isLeft(String name) {
        return name.contains("left")
                || name.startsWith("larm")
                || name.startsWith("lhand")
                || name.startsWith("lleg")
                || name.startsWith("lfoot")
                || name.endsWith("l");
    }

    private static boolean isAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private GeckoAttachmentBridge() {
    }
}
