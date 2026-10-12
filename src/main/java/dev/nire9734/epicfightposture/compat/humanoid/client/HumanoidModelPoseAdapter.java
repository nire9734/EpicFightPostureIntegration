package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.compat.humanoid.LightweightHumanoidPatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Applies EF BIPED action poses only while a lightweight humanoid mob is BROKEN/EXECUTING. */
public final class HumanoidModelPoseAdapter {
    public static void apply(HumanoidModel<?> model, LivingEntity entity) {
        ClientPostureStore.Entry posture = ClientPostureStore.get(entity.getId(), entity.getUUID());
        if (posture == null || !posture.broken) return;

        LivingEntityPatch<?> patch;
        try {
            patch = EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
        } catch (Throwable ignored) {
            return;
        }
        if (!(patch instanceof LightweightHumanoidPatch)) return;

        AnimationPlayer player = patch.getAnimator().getPlayerFor(null);
        if (player == null || player.getAnimation() == null) return;

        AssetAccessor<? extends DynamicAnimation> currentAccessor = player.getAnimation();
        DynamicAnimation currentAnimation = currentAccessor.get();
        StaticAnimation idleAnimation = Animations.BIPED_IDLE.get();
        if (currentAnimation == null || idleAnimation == null) return;

        float partialTick = Minecraft.getInstance().getFrameTime();
        float animationTime = Mth.lerp(partialTick, player.getPrevElapsedTime(), player.getElapsedTime());
        Pose currentPose = currentAnimation.getRawPose(animationTime);
        Pose idlePose = idleAnimation.getRawPose(0.0F);
        if (currentPose == null || idlePose == null) return;

        Armature armature = patch.getArmature();
        if (armature == null) return;

        OpenMatrix4f[] currentMatrices = armature.getPoseAsTransformMatrix(currentPose, false);
        OpenMatrix4f[] idleMatrices = armature.getPoseAsTransformMatrix(idlePose, false);

        /*
         * HumanoidModel's main parts are siblings under the model root. A local EF
         * JointTransform therefore cannot be copied directly to a ModelPart: Head,
         * Arm_R, etc. must also inherit the animated Torso/Chest/Shoulder chain.
         *
         * Use the armature's root-space joint matrices and remove the idle/root-space
         * basis. This also avoids serially multiplying Torso+Chest or Shoulder+Arm,
         * which double-counted the BIPED hierarchy and caused the collapsed pose.
         *
         * These representative joints match Epic Fight's own VanillaModelTransformer:
         * body geometry is based on Torso/Chest, arm geometry begins at Arm_R/L, and
         * leg geometry begins at Thigh_R/L. A rigid HumanoidModel cannot reproduce
         * EF's elbow/knee/chest skinning, so the primary joint is used here.
         */
        setRotationFromGlobal(model.body, armature, currentMatrices, idleMatrices, "Torso");
        setRotationFromGlobal(model.head, armature, currentMatrices, idleMatrices, "Head");
        model.hat.copyFrom(model.head);
        setRotationFromGlobal(model.rightArm, armature, currentMatrices, idleMatrices, "Arm_R");
        setRotationFromGlobal(model.leftArm, armature, currentMatrices, idleMatrices, "Arm_L");
        setRotationFromGlobal(model.rightLeg, armature, currentMatrices, idleMatrices, "Thigh_R");
        setRotationFromGlobal(model.leftLeg, armature, currentMatrices, idleMatrices, "Thigh_L");
    }

    private static void setRotationFromGlobal(
            ModelPart part,
            Armature armature,
            OpenMatrix4f[] currentMatrices,
            OpenMatrix4f[] idleMatrices,
            String jointName) {
        Joint joint = armature.searchJointByName(jointName);
        if (joint == null) return;

        int id = joint.getId();
        if (id < 0 || id >= currentMatrices.length || id >= idleMatrices.length) return;
        OpenMatrix4f current = currentMatrices[id];
        OpenMatrix4f idle = idleMatrices[id];
        if (current == null || idle == null) return;

        Quaternionf currentRotation = current.toQuaternion();
        Quaternionf idleRotation = idle.toQuaternion();

        // Root-space delta that maps the EF idle orientation to the current pose.
        Quaternionf delta = new Quaternionf(currentRotation)
                .mul(new Quaternionf(idleRotation).invert())
                .normalize();
        setRotation(part, delta);
    }

    private static void setRotation(ModelPart part, Quaternionf rotation) {
        Vector3f euler = rotation.getEulerAnglesXYZ(new Vector3f());
        part.xRot = wrapRadians(euler.x);
        part.yRot = wrapRadians(euler.y);
        part.zRot = wrapRadians(euler.z);
    }

    private static float wrapRadians(float value) {
        float twoPi = (float)(Math.PI * 2.0);
        float pi = (float)Math.PI;
        float wrapped = value % twoPi;
        if (wrapped >= pi) wrapped -= twoPi;
        if (wrapped < -pi) wrapped += twoPi;
        return wrapped;
    }

    private HumanoidModelPoseAdapter() {}
}
