package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.Map;

/** Geometric body measurements derived from a completed semantic bind skeleton. */
public record BodyMetrics(
        float totalHeight,
        float shoulderWidth,
        float hipWidth,
        float torsoLength,
        float upperArmLeft,
        float lowerArmLeft,
        float upperArmRight,
        float lowerArmRight,
        float upperLegLeft,
        float lowerLegLeft,
        float upperLegRight,
        float lowerLegRight,
        float pelvisY,
        float chestY,
        float headY
) {
    public BodyMetrics {
        float[] nonNegative = {
                totalHeight, shoulderWidth, hipWidth, torsoLength,
                upperArmLeft, lowerArmLeft, upperArmRight, lowerArmRight,
                upperLegLeft, lowerLegLeft, upperLegRight, lowerLegRight
        };
        for (float value : nonNegative) {
            if (!Float.isFinite(value) || value < 0.0F) {
                throw new IllegalArgumentException("Body lengths must be finite and non-negative");
            }
        }
        if (!Float.isFinite(pelvisY) || !Float.isFinite(chestY) || !Float.isFinite(headY)) {
            throw new IllegalArgumentException("Body anchor heights must be finite");
        }
    }

    public static BodyMetrics derive(Map<HumanoidJoint, JointDescriptor> joints, BodyBounds bounds) {
        return new BodyMetrics(
                bounds.height(),
                distance(joints, HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.SHOULDER_RIGHT),
                distance(joints, HumanoidJoint.HIP_LEFT, HumanoidJoint.HIP_RIGHT),
                distance(joints, HumanoidJoint.PELVIS, HumanoidJoint.CHEST),
                distance(joints, HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.ELBOW_LEFT),
                distance(joints, HumanoidJoint.ELBOW_LEFT, HumanoidJoint.WRIST_LEFT),
                distance(joints, HumanoidJoint.SHOULDER_RIGHT, HumanoidJoint.ELBOW_RIGHT),
                distance(joints, HumanoidJoint.ELBOW_RIGHT, HumanoidJoint.WRIST_RIGHT),
                distance(joints, HumanoidJoint.HIP_LEFT, HumanoidJoint.KNEE_LEFT),
                distance(joints, HumanoidJoint.KNEE_LEFT, HumanoidJoint.ANKLE_LEFT),
                distance(joints, HumanoidJoint.HIP_RIGHT, HumanoidJoint.KNEE_RIGHT),
                distance(joints, HumanoidJoint.KNEE_RIGHT, HumanoidJoint.ANKLE_RIGHT),
                position(joints, HumanoidJoint.PELVIS).y(),
                position(joints, HumanoidJoint.CHEST).y(),
                position(joints, HumanoidJoint.HEAD).y()
        );
    }

    private static float distance(
            Map<HumanoidJoint, JointDescriptor> joints,
            HumanoidJoint first,
            HumanoidJoint second) {
        return position(joints, first).distanceTo(position(joints, second));
    }

    private static SkeletonVec3 position(Map<HumanoidJoint, JointDescriptor> joints, HumanoidJoint joint) {
        JointDescriptor descriptor = joints.get(joint);
        if (descriptor == null) {
            throw new IllegalArgumentException("Missing semantic joint " + joint);
        }
        return descriptor.bindModelTransform().position();
    }
}
