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
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Applies an Epic Fight BIPED action pose to a vanilla HumanoidModel after the
 * owning renderer has finished its normal setupAnim pass.
 *
 * <p>Only BROKEN/EXECUTING compatibility mobs are touched. During every other
 * state the original mod keeps complete ownership of its model animation.</p>
 */
public final class HumanoidModelPoseAdapter {
    public static void apply(HumanoidModel<?> model, LivingEntity entity) {
        ClientPostureStore.Entry posture = ClientPostureStore.get(entity.getId(), entity.getUUID());
        if (posture == null || !posture.broken) {
            return;
        }

        LivingEntityPatch<?> patch;
        try {
            patch = EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
        } catch (Throwable ignored) {
            return;
        }
        if (!(patch instanceof LightweightHumanoidPatch)) {
            return;
        }

        AnimationPlayer player = patch.getAnimator().getPlayerFor(null);
        if (player == null || player.getAnimation() == null) {
            return;
        }

        AssetAccessor<? extends DynamicAnimation> currentAccessor = player.getAnimation();
        DynamicAnimation currentAnimation = currentAccessor.get();
        StaticAnimation idleAnimation = Animations.BIPED_IDLE.get();
        if (currentAnimation == null || idleAnimation == null) {
            return;
        }

        float partialTick = Minecraft.getInstance().getFrameTime();
        float animationTime = Mth.lerp(
                partialTick,
                player.getPrevElapsedTime(),
                player.getElapsedTime()
        );

        // Compare raw animation poses on both sides. This avoids mixing
        // ClientAnimator bind modifiers/layer composition with a raw idle pose.
        Pose currentPose = currentAnimation.getRawPose(animationTime);
        Pose idlePose = idleAnimation.getRawPose(0.0F);
        if (currentPose == null || idlePose == null) {
            return;
        }

        // HumanoidModel has one rigid ModelPart for each of these regions, so
        // collapse the finer Epic Fight chains where necessary.
        setRotation(model.body, delta(currentPose, idlePose, "Torso", "Chest"));
        setRotation(model.head, delta(currentPose, idlePose, "Head"));
        model.hat.copyFrom(model.head);

        setRotation(model.rightArm, delta(currentPose, idlePose, "Shoulder_R", "Arm_R"));
        setRotation(model.leftArm, delta(currentPose, idlePose, "Shoulder_L", "Arm_L"));

        setRotation(model.rightLeg, delta(currentPose, idlePose, "Thigh_R"));
        setRotation(model.leftLeg, delta(currentPose, idlePose, "Thigh_L"));
    }

    private static Quaternionf delta(Pose current, Pose idle, String... joints) {
        Quaternionf currentRotation = chainRotation(current, joints);
        Quaternionf idleRotation = chainRotation(idle, joints);

        // Source-local animation delta: bind^-1 * animated.
        return new Quaternionf(idleRotation)
                .invert()
                .mul(currentRotation)
                .normalize();
    }

    private static Quaternionf chainRotation(Pose pose, String... joints) {
        Quaternionf result = new Quaternionf();
        for (String joint : joints) {
            JointTransform transform = pose.get(joint);
            if (transform != null) {
                result.mul(transform.rotation());
            }
        }
        return result.normalize();
    }

    private static void setRotation(ModelPart part, Quaternionf rotation) {
        Vector3f euler = rotation.getEulerAnglesXYZ(new Vector3f());
        part.xRot = wrapRadians(euler.x);
        part.yRot = wrapRadians(euler.y);
        part.zRot = wrapRadians(euler.z);
    }

    private static float wrapRadians(float value) {
        float twoPi = (float) (Math.PI * 2.0);
        float pi = (float) Math.PI;
        float wrapped = value % twoPi;
        if (wrapped >= pi) {
            wrapped -= twoPi;
        }
        if (wrapped < -pi) {
            wrapped += twoPi;
        }
        return wrapped;
    }

    private HumanoidModelPoseAdapter() {
    }
}
