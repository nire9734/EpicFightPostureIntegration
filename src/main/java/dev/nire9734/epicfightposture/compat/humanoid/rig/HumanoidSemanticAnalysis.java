package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Framework-neutral semantic analysis result before virtual-joint completion. */
public final class HumanoidSemanticAnalysis {
    private static final float COLLAPSED_INTERIOR_EPSILON = 1.0E-5F;

    private final Map<HumanoidJoint, SemanticJointCandidate> candidates;

    public HumanoidSemanticAnalysis(Map<HumanoidJoint, SemanticJointCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        EnumMap<HumanoidJoint, SemanticJointCandidate> copy = new EnumMap<>(HumanoidJoint.class);
        candidates.forEach((joint, candidate) -> {
            Objects.requireNonNull(joint, "semantic joint key");
            Objects.requireNonNull(candidate, "semantic joint candidate");
            if (candidate.joint() != joint) {
                throw new IllegalArgumentException("Candidate joint does not match map key: " + joint);
            }
            copy.put(joint, candidate);
        });

        // A semantically named lower-arm/lower-leg helper can legitimately share
        // its pivot with the parent limb in Blockbench/Gecko-style rigs. Such a
        // source node is not a usable bend joint: keeping it would create a zero-
        // length canonical segment and would also make skinning treat a one-bone
        // limb as explicitly segmented. Drop only the collapsed interior anchor;
        // HumanoidSkeletonResolver will reconstruct it between the surviving
        // proximal and terminal anchors. This rule is framework- and mod-neutral.
        discardCollapsedInterior(copy,
                HumanoidJoint.ELBOW_LEFT,
                HumanoidJoint.SHOULDER_LEFT,
                HumanoidJoint.WRIST_LEFT);
        discardCollapsedInterior(copy,
                HumanoidJoint.ELBOW_RIGHT,
                HumanoidJoint.SHOULDER_RIGHT,
                HumanoidJoint.WRIST_RIGHT);
        discardCollapsedInterior(copy,
                HumanoidJoint.KNEE_LEFT,
                HumanoidJoint.HIP_LEFT,
                HumanoidJoint.ANKLE_LEFT);
        discardCollapsedInterior(copy,
                HumanoidJoint.KNEE_RIGHT,
                HumanoidJoint.HIP_RIGHT,
                HumanoidJoint.ANKLE_RIGHT);

        this.candidates = Map.copyOf(copy);
    }

    private static void discardCollapsedInterior(
            EnumMap<HumanoidJoint, SemanticJointCandidate> candidates,
            HumanoidJoint interiorJoint,
            HumanoidJoint proximalJoint,
            HumanoidJoint terminalJoint) {
        SemanticJointCandidate interior = candidates.get(interiorJoint);
        SemanticJointCandidate proximal = candidates.get(proximalJoint);
        if (interior == null || proximal == null) {
            return;
        }

        if (interior.bindModelPosition().distanceTo(proximal.bindModelPosition())
                <= COLLAPSED_INTERIOR_EPSILON) {
            candidates.remove(interiorJoint);
            return;
        }

        SemanticJointCandidate terminal = candidates.get(terminalJoint);
        if (terminal != null
                && interior.bindModelPosition().distanceTo(terminal.bindModelPosition())
                <= COLLAPSED_INTERIOR_EPSILON) {
            candidates.remove(interiorJoint);
        }
    }

    public Map<HumanoidJoint, SemanticJointCandidate> candidates() {
        return candidates;
    }

    public Optional<SemanticJointCandidate> candidate(HumanoidJoint joint) {
        return Optional.ofNullable(candidates.get(joint));
    }
}
