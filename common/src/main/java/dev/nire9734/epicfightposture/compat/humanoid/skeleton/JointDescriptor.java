package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.Objects;

/**
 * One normalized semantic joint in EFPI model space.
 *
 * <p>The portable humanoid skeleton owns only the anatomical pivot position.
 * Source-framework rotations stay in {@link HumanoidSourceBinding}; animation axes
 * are supplied later by the Epic Fight retargeting layer. This prevents Gecko,
 * Lionfish, Citadel, and vanilla local-axis conventions from leaking into the
 * canonical skeleton.</p>
 */
public record JointDescriptor(
        HumanoidJoint joint,
        SkeletonVec3 bindModelPosition,
        JointOrigin origin,
        float confidence
) {
    public JointDescriptor {
        Objects.requireNonNull(joint, "joint");
        Objects.requireNonNull(bindModelPosition, "bindModelPosition");
        Objects.requireNonNull(origin, "origin");
        if (!Float.isFinite(confidence) || confidence < 0.0F || confidence > 1.0F) {
            throw new IllegalArgumentException("Joint confidence must be within [0, 1]");
        }
    }
}
