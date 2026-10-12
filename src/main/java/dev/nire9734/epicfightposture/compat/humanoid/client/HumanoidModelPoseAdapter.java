package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.compat.humanoid.LightweightHumanoidPatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Applies an Epic Fight BIPED action pose to a vanilla-style HumanoidModel only
 * while the lightweight compatibility patch is BROKEN/EXECUTING.
 *
 * <p>This adapter intentionally keeps the third-party renderer and normal model
 * animation untouched outside those states.</p>
 */
public final class HumanoidModelPoseAdapter {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    /*
     * Epic Fight's VanillaModelTransformer uses exactly this model-space change
     * before baking HumanoidModel cubes into its BIPED skinned mesh:
     *
     *   rotate Y 180 -> rotate X 180 -> translate Y -24 px
     *
     * Use the same transform in block units so an EF skinning matrix can be
     * conjugated back into the coordinate system expected by ModelPart.
     */
    private static final Matrix4f VANILLA_TO_EF = new Matrix4f()
            .rotateY((float)Math.PI)
            .rotateX((float)Math.PI)
            .translate(0.0F, -24.0F / PIXELS_PER_BLOCK, 0.0F);
    private static final Matrix4f EF_TO_VANILLA = new Matrix4f(VANILLA_TO_EF).invert();

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

        Armature armature = patch.getArmature();
        if (armature == null) return;

        float partialTick = Minecraft.getInstance().getFrameTime();

        /*
         * Use the same composed pose that Epic Fight's patched renderer uses.
         * Sampling DynamicAnimation#getRawPose here loses layer composition and
         * bind modifiers, which was one source of the previous malformed poses.
         */
        Pose pose = patch.getAnimator().getPose(partialTick);
        if (pose == null) return;

        /*
         * applyOriginTransform=true yields each joint's skinning/deformation
         * matrix (current global joint transform * inverse bind transform).
         * This directly removes the BIPED bind axes. The previous implementation
         * compared current/idle global orientations by hand, which does not equal
         * the matrix Epic Fight actually applies to its mesh.
         */
        OpenMatrix4f[] deformationMatrices = armature.getPoseAsTransformMatrix(pose, true);

        // Rigid HumanoidModel parts cannot reproduce EF chest/elbow/knee skinning,
        // so use the representative primary joint for each rigid part.
        applyJoint(model.body, armature, deformationMatrices, "Chest");
        applyJoint(model.head, armature, deformationMatrices, "Head");
        model.hat.copyFrom(model.head);
        applyJoint(model.rightArm, armature, deformationMatrices, "Arm_R");
        applyJoint(model.leftArm, armature, deformationMatrices, "Arm_L");
        applyJoint(model.rightLeg, armature, deformationMatrices, "Thigh_R");
        applyJoint(model.leftLeg, armature, deformationMatrices, "Thigh_L");
    }

    private static void applyJoint(
            ModelPart part,
            Armature armature,
            OpenMatrix4f[] deformationMatrices,
            String jointName) {
        Joint joint = armature.searchJointByName(jointName);
        if (joint == null) return;

        int id = joint.getId();
        if (id < 0 || id >= deformationMatrices.length) return;
        OpenMatrix4f deformation = deformationMatrices[id];
        if (deformation == null) return;

        /*
         * If T0 is the ModelPart's bind transform and C is the exact conversion
         * used by VanillaModelTransformer, a rigid approximation of Epic Fight's
         * skinned result is:
         *
         *     T = C^-1 * D * C * T0
         *
         * where D is the EF joint skinning matrix. Unlike the old rotation-only
         * adapter this also moves the ModelPart pivot, which is required because
         * HumanoidModel's head/arms/legs are siblings instead of children of body.
         */
        Matrix4f d = OpenMatrix4f.exportToMojangMatrix(deformation);
        Matrix4f bind = initialTransform(part.getInitialPose());
        Matrix4f desired = new Matrix4f(EF_TO_VANILLA)
                .mul(d)
                .mul(VANILLA_TO_EF)
                .mul(bind);

        Vector3f translation = desired.getTranslation(new Vector3f());
        part.x = translation.x * PIXELS_PER_BLOCK;
        part.y = translation.y * PIXELS_PER_BLOCK;
        part.z = translation.z * PIXELS_PER_BLOCK;

        Quaternionf rotation = desired.getNormalizedRotation(new Quaternionf()).normalize();

        /*
         * ModelPart#translateAndRotate constructs rotationZYX(zRot, yRot, xRot),
         * so extracting XYZ Euler angles was mathematically inconsistent for
         * combined rotations. getEulerAnglesZYX is the matching decomposition.
         */
        Vector3f euler = rotation.getEulerAnglesZYX(new Vector3f());
        part.xRot = wrapRadians(euler.x);
        part.yRot = wrapRadians(euler.y);
        part.zRot = wrapRadians(euler.z);
    }

    private static Matrix4f initialTransform(PartPose pose) {
        return new Matrix4f()
                .translation(
                        pose.x / PIXELS_PER_BLOCK,
                        pose.y / PIXELS_PER_BLOCK,
                        pose.z / PIXELS_PER_BLOCK)
                .rotate(new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot));
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
