package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.Objects;

/** One normalized semantic joint in the authored bind pose. */
public record JointDescriptor(
        HumanoidJoint joint,
        RigidTransform bindModelTransform,
        JointOrigin origin,
        float confidence
) {
    public JointDescriptor {
        Objects.requireNonNull(joint, "joint");
        Objects.requireNonNull(bindModelTransform, "bindModelTransform");
        Objects.requireNonNull(origin, "origin");
        if (!Float.isFinite(confidence) || confidence < 0.0F || confidence > 1.0F) {
            throw new IllegalArgumentException("Joint confidence must be within [0, 1]");
        }
    }
}
