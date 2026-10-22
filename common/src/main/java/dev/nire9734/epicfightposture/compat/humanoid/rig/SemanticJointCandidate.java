package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;

import java.util.Objects;
import java.util.Optional;

/** One proposed semantic joint produced by a humanoid analyzer. */
public record SemanticJointCandidate(
        HumanoidJoint joint,
        SkeletonVec3 bindModelPosition,
        Optional<String> sourceNodeId,
        JointOrigin origin,
        float confidence
) {
    public SemanticJointCandidate {
        Objects.requireNonNull(joint, "joint");
        Objects.requireNonNull(bindModelPosition, "bindModelPosition");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        Objects.requireNonNull(origin, "origin");
        if (!Float.isFinite(confidence) || confidence < 0.0F || confidence > 1.0F) {
            throw new IllegalArgumentException("Candidate confidence must be within [0, 1]");
        }
        if (origin == JointOrigin.SOURCE && sourceNodeId.isEmpty()) {
            throw new IllegalArgumentException("SOURCE candidate requires a source node id");
        }
        if (origin == JointOrigin.VIRTUAL && sourceNodeId.isPresent()) {
            throw new IllegalArgumentException("VIRTUAL candidate must not reference a source node");
        }
    }

    public static SemanticJointCandidate fromSource(
            SourceRigSnapshot snapshot,
            HumanoidJoint joint,
            String sourceNodeId,
            JointOrigin origin,
            float confidence) {
        SourceNodeSnapshot node = snapshot.node(sourceNodeId);
        return new SemanticJointCandidate(
                joint,
                node.bindModelTransform().position(),
                Optional.of(sourceNodeId),
                origin,
                confidence
        );
    }

    public static SemanticJointCandidate virtual(
            HumanoidJoint joint,
            SkeletonVec3 position,
            float confidence) {
        return new SemanticJointCandidate(
                joint,
                position,
                Optional.empty(),
                JointOrigin.VIRTUAL,
                confidence
        );
    }
}
