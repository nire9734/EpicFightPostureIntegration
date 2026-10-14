package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.Objects;

/**
 * One normalized semantic joint in the authored bind pose.
 *
 * <p>The rotation is a normalized semantic frame, not necessarily the native local
 * rotation of the source model node. Framework-specific source transforms belong in
 * {@link HumanoidSourceBinding}; this keeps animation axes stable across model APIs.</p>
 */
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
