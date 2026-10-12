package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.compat.humanoid.LightweightHumanoidPatch;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Native animation backend for vanilla-style HumanoidModel mobs.
 *
 * <p>Unlike the previous experimental adapter, this class does not retarget an
 * Epic Fight BIPED pose. The lightweight Epic Fight patch still exists so Combat
 * Evolution can recognize/execute the mob, but the rendered guard-break pose is
 * authored directly for HumanoidModel's seven useful channels: a whole-model
 * root transform plus head/body/arms/legs.</p>
 */
public final class HumanoidModelPoseAdapter {
    /** Approximate HumanoidModel foot plane measured from the model root. */
    private static final float ROOT_PIVOT_Y = 24.0F / 16.0F;

    /** One client-side animation clock per live entity. Weak keys avoid stale entries. */
    private static final Map<LivingEntity, BreakState> BREAK_STATES = new WeakHashMap<>();

    /**
     * Called after the concrete model's setupAnim() has finished.
     *
     * @return true when EFPI replaced the model pose for this frame.
     */
    public static boolean apply(
            HumanoidModel<?> model,
            LivingEntity entity,
            PoseStack poseStack,
            float partialTick) {
        ClientPostureStore.Entry posture = ClientPostureStore.get(entity.getId(), entity.getUUID());
        boolean broken = posture != null && posture.broken;

        // Only entities carrying our non-AI-replacing compatibility patch are eligible.
        LivingEntityPatch<?> patch;
        try {
            patch = EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
        } catch (Throwable ignored) {
            BREAK_STATES.remove(entity);
            return false;
        }
        if (!(patch instanceof LightweightHumanoidPatch)) {
            BREAK_STATES.remove(entity);
            return false;
        }

        if (!broken) {
            BREAK_STATES.remove(entity);
            return false;
        }

        float now = entity.tickCount + Mth.clamp(partialTick, 0.0F, 1.0F);
        BreakState state = BREAK_STATES.computeIfAbsent(entity, ignored -> new BreakState(now));
        float age = Math.max(0.0F, now - state.startTick);

        applyGuardBreak(model, poseStack, age);
        return true;
    }

    /**
     * Original HumanoidModel guard-break animation.
     *
     * <p>Motion design:
     * <ol>
     *   <li>small hit recoil;</li>
     *   <li>the entire character loses balance and falls backward;</li>
     *   <li>arms open asymmetrically while the knees/hips loosen;</li>
     *   <li>the target settles into a held vulnerable pose.</li>
     * </ol>
     *
     * <p>The crucial difference from the old implementation is the root rotation:
     * every body part, including both legs, is transformed together around the
     * approximate foot plane. Local ModelPart rotations are only secondary posing,
     * so the torso cannot visually detach from the legs.</p>
     */
    private static void applyGuardBreak(HumanoidModel<?> model, PoseStack poseStack, float ageTicks) {
        // 0..~11 ticks: main fall. ~11..14: a small settle/bounce.
        float recoil = smootherStep(ageTicks / 2.0F);
        float fall = smootherStep((ageTicks - 1.0F) / 10.0F);
        float settle = smootherStep((ageTicks - 10.0F) / 4.0F);

        // Backward whole-body fall. In vanilla pitch convention negative X is upward/backward.
        float rootPitchDeg = 8.0F * recoil + 74.0F * fall - 7.0F * settle;

        // Rotate around the feet rather than around the head/model origin.
        poseStack.translate(0.0F, ROOT_PIVOT_Y, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-rootPitchDeg));
        poseStack.translate(0.0F, -ROOT_PIVOT_Y, 0.0F);

        // Discard whatever walk/attack pose the source mod produced for this one special state,
        // but preserve each model's authored pivot positions and bind rotations.
        reset(model.head);
        reset(model.body);
        reset(model.rightArm);
        reset(model.leftArm);
        reset(model.rightLeg);
        reset(model.leftLeg);

        // Secondary pose layered inside the root fall. Angles are deliberately modest because
        // HumanoidModel has no chest, elbow, knee, or hand bones.
        model.body.xRot += radians(10.0F * fall - 4.0F * settle);
        model.body.zRot += radians(4.0F * fall);

        // Keep the face somewhat readable instead of letting the head follow the torso rigidly.
        model.head.xRot += radians(17.0F * fall - 3.0F * settle);
        model.head.yRot += radians(-8.0F * fall);
        model.head.zRot += radians(-5.0F * fall);

        // Guard breaks open the silhouette: one arm drops, the other is thrown outward.
        model.rightArm.xRot += radians(-48.0F * fall + 8.0F * settle);
        model.rightArm.yRot += radians(-10.0F * fall);
        model.rightArm.zRot += radians(-28.0F * fall);

        model.leftArm.xRot += radians(-62.0F * fall + 10.0F * settle);
        model.leftArm.yRot += radians(12.0F * fall);
        model.leftArm.zRot += radians(34.0F * fall);

        // The legs stay attached to the falling root, with only a small stagger added locally.
        model.rightLeg.xRot += radians(17.0F * fall);
        model.rightLeg.zRot += radians(-4.0F * fall);
        model.leftLeg.xRot += radians(-11.0F * fall);
        model.leftLeg.zRot += radians(5.0F * fall);

        model.hat.copyFrom(model.head);
    }

    private static void reset(ModelPart part) {
        PartPose initial = part.getInitialPose();
        part.x = initial.x;
        part.y = initial.y;
        part.z = initial.z;
        part.xRot = initial.xRot;
        part.yRot = initial.yRot;
        part.zRot = initial.zRot;
    }

    private static float smootherStep(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    private static float radians(float degrees) {
        return degrees * ((float)Math.PI / 180.0F);
    }

    private record BreakState(float startTick) {}

    private HumanoidModelPoseAdapter() {}
}
