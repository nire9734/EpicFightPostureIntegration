package dev.nire9734.epicfightposture.compat.humanoid.retarget;

import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SemanticJointCandidate;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps framework-neutral source geometry to Epic Fight BIPED skin weights.
 *
 * <p>Explicit semantic source joints are rigidly assigned to the corresponding
 * BIPED deformation joint. If an arm or leg has no explicit elbow/knee source
 * node, geometry on that one-bone limb is smoothly blended across the inferred
 * semantic joint instead of physically splitting the source mesh.</p>
 */
public final class HumanoidSkinWeightResolver {
    private static final float EPSILON = 1.0E-6F;
    private static final float TRANSITION_HALF_WIDTH = 0.08F;

    // Epic Fight 20.14.x canonical BIPED deformation joint ids.
    public static final int ROOT = 0;
    public static final int THIGH_RIGHT = 1;
    public static final int LEG_RIGHT = 2;
    public static final int THIGH_LEFT = 4;
    public static final int LEG_LEFT = 5;
    public static final int CHEST = 8;
    public static final int HEAD = 9;
    public static final int ARM_RIGHT = 11;
    public static final int HAND_RIGHT = 12;
    public static final int ARM_LEFT = 16;
    public static final int HAND_LEFT = 17;

    private final SourceRigSnapshot snapshot;
    private final HumanoidSkeletonDescriptor descriptor;
    private final Map<String, HumanoidJoint> directOwners;
    private final boolean explicitRightElbow;
    private final boolean explicitLeftElbow;
    private final boolean explicitRightKnee;
    private final boolean explicitLeftKnee;

    public HumanoidSkinWeightResolver(
            SourceRigSnapshot snapshot,
            HumanoidSemanticAnalysis analysis,
            HumanoidSkeletonDescriptor descriptor) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(analysis, "analysis");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");

        Map<String, HumanoidJoint> owners = new HashMap<>();
        for (SemanticJointCandidate candidate : analysis.candidates().values()) {
            // Geometry-inferred and virtual candidates often point back to the
            // upper limb source node. Treating those references as direct owners
            // would incorrectly reassign the entire limb to its terminal joint.
            if (candidate.origin() != JointOrigin.SOURCE) {
                continue;
            }
            candidate.sourceNodeId().ifPresent(sourceId ->
                    owners.putIfAbsent(sourceId, candidate.joint()));
        }
        this.directOwners = Map.copyOf(owners);

        this.explicitRightElbow = hasDirectSource(analysis, HumanoidJoint.ELBOW_RIGHT);
        this.explicitLeftElbow = hasDirectSource(analysis, HumanoidJoint.ELBOW_LEFT);
        this.explicitRightKnee = hasDirectSource(analysis, HumanoidJoint.KNEE_RIGHT);
        this.explicitLeftKnee = hasDirectSource(analysis, HumanoidJoint.KNEE_LEFT);
    }

    public SkinWeights weights(String sourceNodeId, SkeletonVec3 position) {
        Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        Objects.requireNonNull(position, "position");

        HumanoidJoint owner = nearestSemanticOwner(sourceNodeId);
        if (owner == null) {
            return SkinWeights.rigid(ROOT);
        }

        return switch (owner) {
            case ROOT, SPINE -> SkinWeights.rigid(ROOT);
            case CHEST -> SkinWeights.rigid(CHEST);
            case NECK, HEAD -> SkinWeights.rigid(HEAD);

            case SHOULDER_RIGHT -> explicitRightElbow
                    ? SkinWeights.rigid(ARM_RIGHT)
                    : limbBlend(
                            position,
                            HumanoidJoint.SHOULDER_RIGHT,
                            HumanoidJoint.ELBOW_RIGHT,
                            HumanoidJoint.WRIST_RIGHT,
                            ARM_RIGHT,
                            HAND_RIGHT);
            case ELBOW_RIGHT, WRIST_RIGHT -> SkinWeights.rigid(HAND_RIGHT);

            case SHOULDER_LEFT -> explicitLeftElbow
                    ? SkinWeights.rigid(ARM_LEFT)
                    : limbBlend(
                            position,
                            HumanoidJoint.SHOULDER_LEFT,
                            HumanoidJoint.ELBOW_LEFT,
                            HumanoidJoint.WRIST_LEFT,
                            ARM_LEFT,
                            HAND_LEFT);
            case ELBOW_LEFT, WRIST_LEFT -> SkinWeights.rigid(HAND_LEFT);

            case HIP_RIGHT -> explicitRightKnee
                    ? SkinWeights.rigid(THIGH_RIGHT)
                    : limbBlend(
                            position,
                            HumanoidJoint.HIP_RIGHT,
                            HumanoidJoint.KNEE_RIGHT,
                            HumanoidJoint.ANKLE_RIGHT,
                            THIGH_RIGHT,
                            LEG_RIGHT);
            case KNEE_RIGHT, ANKLE_RIGHT -> SkinWeights.rigid(LEG_RIGHT);

            case HIP_LEFT -> explicitLeftKnee
                    ? SkinWeights.rigid(THIGH_LEFT)
                    : limbBlend(
                            position,
                            HumanoidJoint.HIP_LEFT,
                            HumanoidJoint.KNEE_LEFT,
                            HumanoidJoint.ANKLE_LEFT,
                            THIGH_LEFT,
                            LEG_LEFT);
            case KNEE_LEFT, ANKLE_LEFT -> SkinWeights.rigid(LEG_LEFT);
        };
    }

    private HumanoidJoint nearestSemanticOwner(String sourceNodeId) {
        SourceNodeSnapshot cursor = snapshot.node(sourceNodeId);
        while (true) {
            HumanoidJoint owner = directOwners.get(cursor.id());
            if (owner != null) {
                return owner;
            }
            if (cursor.parentId().isEmpty()) {
                return null;
            }
            cursor = snapshot.node(cursor.parentId().orElseThrow());
        }
    }

    private SkinWeights limbBlend(
            SkeletonVec3 position,
            HumanoidJoint startJoint,
            HumanoidJoint bendJoint,
            HumanoidJoint endJoint,
            int upperJointId,
            int lowerJointId) {
        SkeletonVec3 start = descriptor.bindModelPosition(startJoint);
        SkeletonVec3 bend = descriptor.bindModelPosition(bendJoint);
        SkeletonVec3 end = descriptor.bindModelPosition(endJoint);
        SkeletonVec3 axis = end.subtract(start);
        float denominator = axis.lengthSquared();
        if (denominator <= EPSILON) {
            return SkinWeights.rigid(upperJointId);
        }

        float t = dot(position.subtract(start), axis) / denominator;
        float bendT = dot(bend.subtract(start), axis) / denominator;
        if (!Float.isFinite(t) || !Float.isFinite(bendT)) {
            return SkinWeights.rigid(upperJointId);
        }

        float lowerEdge = bendT - TRANSITION_HALF_WIDTH;
        float upperEdge = bendT + TRANSITION_HALF_WIDTH;
        if (t <= lowerEdge) {
            return SkinWeights.rigid(upperJointId);
        }
        if (t >= upperEdge) {
            return SkinWeights.rigid(lowerJointId);
        }

        float lowerWeight = clamp01((t - lowerEdge) / Math.max(EPSILON, upperEdge - lowerEdge));
        return SkinWeights.blend(upperJointId, lowerJointId, 1.0F - lowerWeight, lowerWeight);
    }

    private static boolean hasDirectSource(
            HumanoidSemanticAnalysis analysis,
            HumanoidJoint joint) {
        return analysis.candidate(joint)
                .filter(candidate -> candidate.origin() == JointOrigin.SOURCE)
                .flatMap(SemanticJointCandidate::sourceNodeId)
                .isPresent();
    }

    private static float dot(SkeletonVec3 first, SkeletonVec3 second) {
        return first.x() * second.x() + first.y() * second.y() + first.z() * second.z();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public record SkinWeights(
            int firstJoint,
            int secondJoint,
            float firstWeight,
            float secondWeight,
            int count) {
        public SkinWeights {
            if (count < 1 || count > 2) {
                throw new IllegalArgumentException("Humanoid skin weight count must be 1 or 2");
            }
            if (!Float.isFinite(firstWeight) || !Float.isFinite(secondWeight)) {
                throw new IllegalArgumentException("Humanoid skin weights must be finite");
            }
        }

        public static SkinWeights rigid(int joint) {
            return new SkinWeights(joint, 0, 1.0F, 0.0F, 1);
        }

        public static SkinWeights blend(
                int firstJoint,
                int secondJoint,
                float firstWeight,
                float secondWeight) {
            float sum = firstWeight + secondWeight;
            if (sum <= EPSILON) {
                return rigid(firstJoint);
            }
            return new SkinWeights(
                    firstJoint,
                    secondJoint,
                    firstWeight / sum,
                    secondWeight / sum,
                    2
            );
        }
    }
}
