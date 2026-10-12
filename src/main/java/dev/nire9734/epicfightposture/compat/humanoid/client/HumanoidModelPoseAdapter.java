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

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies an Epic Fight BIPED action pose to vanilla HumanoidModel renderers.
 *
 * <p>The entity/model association is remembered during HumanoidModel.setupAnim,
 * while the actual override is deliberately deferred until renderToBuffer. This
 * matters for third-party HumanoidModel subclasses which call super.setupAnim()
 * and then apply their own extra animation afterwards: EFPI still gets the last
 * word only while BROKEN/EXECUTING.</p>
 */
public final class HumanoidModelPoseAdapter {
    private static final Map<HumanoidModel<?>, LivingEntity> LAST_ENTITY =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void remember(HumanoidModel<?> model, LivingEntity entity) {
        if (model != null && entity != null) {
            LAST_ENTITY.put(model, entity);
        }
    }

    public static void applyRemembered(HumanoidModel<?> model) {
        LivingEntity entity = LAST_ENTITY.get(model);
        if (entity != null) {
            apply(model, entity);
        }
    }

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

        // Both poses are raw animation poses. Do not compare a ClientAnimator
        // composed/bind-modified pose against a raw idle pose.
        Pose currentPose = currentAnimation.getRawPose(animationTime);
        Pose idlePose = idleAnimation.getRawPose(0.0F);
        if (currentPose == null || idlePose == null) {
            return;
        }

        // Vanilla HumanoidModel has one rigid ModelPart for each coarse limb, so
        // finer Epic Fight chains are collapsed to the nearest available part.
        setRotation(model.body, delta(currentPose, idlePose, "Torso", "Chest"));
        setRotation(model.head, delta(currentPose, idlePose, "Head"));
        model.hat.copyFrom(model.head);

        setRotation(model.rightArm, delta(currentPose, idlePose, "Shoulder_R", "Arm_R"));
        setRotation(model.leftArm, delta(currentPose, idlePose, "Shoulder_L", "Arm_L"));

        // HumanoidModel has no knee joint. Preserve the thigh/hip direction and
        // intentionally drop Epic Fight lower-leg articulation.
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
