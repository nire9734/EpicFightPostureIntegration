package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Framework-neutral semantic analysis result before virtual-joint completion. */
public final class HumanoidSemanticAnalysis {
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
        this.candidates = Map.copyOf(copy);
    }

    public Map<HumanoidJoint, SemanticJointCandidate> candidates() {
        return candidates;
    }

    public Optional<SemanticJointCandidate> candidate(HumanoidJoint joint) {
        return Optional.ofNullable(candidates.get(joint));
    }
}
