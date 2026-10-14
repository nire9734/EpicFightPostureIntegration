package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.util.RenderUtils;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.Vec3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Narrow compatibility scope for Gecko armor callbacks.
 *
 * <p>The attachment bridge normally replays every layer from an exact Epic Fight
 * skin matrix. That remains the authoritative path for helmets, sleeves, boots,
 * held items and arbitrary decoration layers. A Gecko {@code ItemArmorGeoLayer}
 * body callback is special, however: it also consumes the live source bone's
 * pivot/rotation to prepare its HumanoidModel/GeoArmorRenderer. For torso armor,
 * replaying that callback from the synchronized source-local pose proved to be
 * substantially more faithful than replacing the armor renderer's geometry.</p>
 *
 * <p>This scope therefore changes only the PoseStack seen by torso-like armor
 * callbacks. It never cancels GeckoArmorRenderer rendering and never mutates the
 * shared source bones. The original exact matrix is restored as soon as the
 * callback returns.</p>
 */
public final class GeckoArmorBodySkinningContext {
    private static final float MATRIX_EPSILON = 1.0E-8F;

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

    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    public static FrameScope openFrame(
            LivingEntity entity,
            Armature armature,
            HumanoidSkeletonDescriptor skeleton,
            PoseStack basePoseStack,
            Pose efPose) {
        Frame previous = ACTIVE.get();
        if (entity == null
                || armature == null
                || skeleton == null
                || basePoseStack == null
                || efPose == null) {
            return new FrameScope(previous, false);
        }

        ACTIVE.set(new Frame(
                entity,
                armature,
                efPose,
                new Matrix4f(basePoseStack.last().pose())
        ));
        return new FrameScope(previous, true);
    }

    /** Called at ItemArmorGeoLayer.renderForBone HEAD. */
    public static void beginArmorBone(
            PoseStack poseStack,
            LivingEntity entity,
            GeoBone sourceBone) {
        Frame frame = ACTIVE.get();
        if (frame == null || poseStack == null || entity != frame.entity) {
            return;
        }

        boolean pushedOverride = false;
        if (sourceBone != null && isTorsoArmorHost(sourceBone)) {
            Matrix4f sourcePose = animatedAbsoluteSourcePose(
                    sourceBone,
                    frame.armature,
                    frame.efPose
            );
            if (sourcePose != null) {
                Matrix4f finalPose = new Matrix4f(frame.basePose).mul(sourcePose);
                Matrix3f finalNormal = normalMatrix(finalPose);
                if (finalNormal != null) {
                    poseStack.pushPose();
                    poseStack.last().pose().set(finalPose);
                    poseStack.last().normal().set(finalNormal);
                    pushedOverride = true;
                }
            }
        }

        frame.overrideScopes.push(pushedOverride);
    }

    /** Called at ItemArmorGeoLayer.renderForBone RETURN. */
    public static void endArmorBone(PoseStack poseStack) {
        Frame frame = ACTIVE.get();
        if (frame == null || frame.overrideScopes.isEmpty()) {
            return;
        }

        boolean pushedOverride = frame.overrideScopes.pop();
        if (pushedOverride && poseStack != null) {
            poseStack.popPose();
        }
    }

    /**
     * Rebuilds the source bone's absolute transform using the same selective
     * semantic projection used by GeckoAttachmentBridge's post-render sync.
     * Unknown helper bones remain in authored bind pose and inherit the nearest
     * semantic parent normally.
     */
    private static Matrix4f animatedAbsoluteSourcePose(
            GeoBone sourceBone,
            Armature armature,
            Pose efPose) {
        List<GeoBone> chain = new ArrayList<>();
        GeoBone cursor = sourceBone;
        while (cursor != null) {
            chain.add(0, cursor);
            cursor = cursor.getParent();
        }
        if (chain.isEmpty()) {
            return null;
        }

        PoseStack stack = new PoseStack();
        int inheritedJoint = -1;
        for (GeoBone bone : chain) {
            Integer explicitJoint = classifyJoint(bone, inheritedJoint);
            int jointId = explicitJoint != null
                    ? explicitJoint
                    : (inheritedJoint >= 0 ? inheritedJoint : ROOT);

            boolean semanticTransition = inheritedJoint < 0
                    || (explicitJoint != null && jointId != inheritedJoint);
            if (semanticTransition) {
                applyAnimatedBoneTransform(stack, bone, armature, efPose, jointId);
            } else {
                RenderUtils.prepMatrixForBone(stack, bone);
            }
            inheritedJoint = jointId;
        }
        return new Matrix4f(stack.last().pose());
    }

    private static void applyAnimatedBoneTransform(
            PoseStack stack,
            GeoBone bone,
            Armature armature,
            Pose efPose,
            int jointId) {
        BoneSnapshot snapshot = bone.getInitialSnapshot();
        Joint joint = armature.searchJointById(jointId);
        if (snapshot == null || joint == null) {
            RenderUtils.prepMatrixForBone(stack, bone);
            return;
        }

        JointTransform transform = efPose.orElseEmpty(joint.getName());
        Vec3f translation = transform.translation();
        Vec3f scale = transform.scale();
        float[] rotation = quaternionToZyxEuler(transform.rotation());

        float posX = snapshot.getOffsetX() - translation.x * 16.0F;
        float posY = snapshot.getOffsetY() + translation.y * 16.0F;
        float posZ = snapshot.getOffsetZ() + translation.z * 16.0F;
        float rotX = snapshot.getRotX() - rotation[0];
        float rotY = snapshot.getRotY() - rotation[1];
        float rotZ = snapshot.getRotZ() - rotation[2];
        float scaleX = snapshot.getScaleX() * scale.x;
        float scaleY = snapshot.getScaleY() * scale.y;
        float scaleZ = snapshot.getScaleZ() * scale.z;

        stack.translate(-posX / 16.0F, posY / 16.0F, posZ / 16.0F);
        stack.translate(
                bone.getPivotX() / 16.0F,
                bone.getPivotY() / 16.0F,
                bone.getPivotZ() / 16.0F
        );
        if (rotZ != 0.0F) {
            stack.mulPose(Axis.ZP.rotation(rotZ));
        }
        if (rotY != 0.0F) {
            stack.mulPose(Axis.YP.rotation(rotY));
        }
        if (rotX != 0.0F) {
            stack.mulPose(Axis.XP.rotation(rotX));
        }
        stack.scale(scaleX, scaleY, scaleZ);
        stack.translate(
                -bone.getPivotX() / 16.0F,
                -bone.getPivotY() / 16.0F,
                -bone.getPivotZ() / 16.0F
        );
    }

    private static boolean isTorsoArmorHost(GeoBone bone) {
        String name = normalize(bone.getName());
        if (name.isEmpty()) {
            return false;
        }
        if (containsAny(name,
                "head", "skull", "helmet", "hat",
                "arm", "hand", "wrist", "sleeve",
                "leg", "foot", "boot", "thigh", "knee")) {
            return false;
        }
        return containsAny(name, "body", "torso", "chest", "trunk", "spine");
    }

    private static Matrix3f normalMatrix(Matrix4f matrix) {
        Matrix3f normal = new Matrix3f(matrix);
        float determinant = normal.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) <= MATRIX_EPSILON) {
            return null;
        }
        return normal.invert().transpose();
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
        if (bone.getChildBones() == null) {
            return false;
        }
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

    public static final class FrameScope implements AutoCloseable {
        private final Frame previous;
        private final boolean installed;
        private boolean closed;

        private FrameScope(Frame previous, boolean installed) {
            this.previous = previous;
            this.installed = installed;
        }

        @Override
        public void close() {
            if (closed || !installed) {
                closed = true;
                return;
            }
            closed = true;
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    private static final class Frame {
        private final LivingEntity entity;
        private final Armature armature;
        private final Pose efPose;
        private final Matrix4f basePose;
        private final ArrayDeque<Boolean> overrideScopes = new ArrayDeque<>();

        private Frame(
                LivingEntity entity,
                Armature armature,
                Pose efPose,
                Matrix4f basePose) {
            this.entity = entity;
            this.armature = armature;
            this.efPose = efPose;
            this.basePose = basePose;
        }
    }

    private GeckoArmorBodySkinningContext() {
    }
}
