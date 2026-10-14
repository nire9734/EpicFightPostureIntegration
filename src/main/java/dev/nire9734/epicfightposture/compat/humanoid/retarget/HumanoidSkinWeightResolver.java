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
 * <p>Explicit semantic source joints are assigned to the corresponding BIPED
 * deformation chain. One-bone limbs are smoothly blended across their inferred
 * elbow/knee. The torso is also split per vertex across the inferred spine so a
 * single source {@code body}/{@code torso} node can still participate in Epic
 * Fight's independent lower-body (Root) and upper-body (Chest) motion.</p>
 */
public final class HumanoidSkinWeightResolver {
    private static final float EPSILON = 1.0E-6F;
    private static final float TRANSITION_SHORT_SEGMENT_FACTOR = 0.16F;
    private static final float MIN_TRANSITION_HALF_WIDTH = 0.025F;
    private static final float MAX_TRANSITION_HALF_WIDTH = 0.12F;

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
            // Do not make the torso split depend on source-node boundaries. A
            // large number of Gecko/Blockbench models put the complete abdomen
            // and chest in one source bone. Vertex-space weighting preserves the
            // BIPED Root/Chest separation for those rigs without model-specific
            // names or hard-coded dimensions.
            case ROOT, SPINE, CHEST -> chainBlend(
                    position,
                    HumanoidJoint.ROOT,
                    HumanoidJoint.SPINE,
                    HumanoidJoint.CHEST,
                    ROOT,
                    CHEST);
            case NECK, HEAD -> SkinWeights.rigid(HEAD);

            case SHOULDER_RIGHT -> explicitRightElbow
                    ? SkinWeights.rigid(ARM_RIGHT)
                    : chainBlend(
                            position,
                            HumanoidJoint.SHOULDER_RIGHT,
                            HumanoidJoint.ELBOW_RIGHT,
                            HumanoidJoint.WRIST_RIGHT,
                            ARM_RIGHT,
                            HAND_RIGHT);
            case ELBOW_RIGHT, WRIST_RIGHT -> SkinWeights.rigid(HAND_RIGHT);

            case SHOULDER_LEFT -> explicitLeftElbow
                    ? SkinWeights.rigid(ARM_LEFT)
                    : chainBlend(
                            position,
                            HumanoidJoint.SHOULDER_LEFT,
                            HumanoidJoint.ELBOW_LEFT,
                            HumanoidJoint.WRIST_LEFT,
                            ARM_LEFT,
                            HAND_LEFT);
            case ELBOW_LEFT, WRIST_LEFT -> SkinWeights.rigid(HAND_LEFT);

            case HIP_RIGHT -> explicitRightKnee
                    ? SkinWeights.rigid(THIGH_RIGHT)
                    : chainBlend(
                            position,
                            HumanoidJoint.HIP_RIGHT,
                            HumanoidJoint.KNEE_RIGHT,
                            HumanoidJoint.ANKLE_RIGHT,
                            THIGH_RIGHT,
                            LEG_RIGHT);
            case KNEE_RIGHT, ANKLE_RIGHT -> SkinWeights.rigid(LEG_RIGHT);

            case HIP_LEFT -> explicitLeftKnee
                    ? SkinWeights.rigid(THIGH_LEFT)
                    : chainBlend(
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

    private SkinWeights chainBlend(
            SkeletonVec3 position,
            HumanoidJoint startJoint,
            HumanoidJoint bendJoint,
            HumanoidJoint endJoint,
            int proximalJointId,
            int distalJointId) {
        SkeletonVec3 start = descriptor.bindModelPosition(startJoint);
        SkeletonVec3 bend = descriptor.bindModelPosition(bendJoint);
        SkeletonVec3 end = descriptor.bindModelPosition(endJoint);
        float distalWeight = distalBlendWeight(position, start, bend, end);

        if (distalWeight <= EPSILON) {
            return SkinWeights.rigid(proximalJointId);
        }
        if (distalWeight >= 1.0F - EPSILON) {
            return SkinWeights.rigid(distalJointId);
        }
        return SkinWeights.blend(
                proximalJointId,
                distalJointId,
                1.0F - distalWeight,
                distalWeight
        );
    }

    /**
     * Returns the distal-joint weight for a point projected onto a three-point
     * chain. Package-private so scale/proportion invariants can be regression
     * tested independently of a concrete Minecraft model implementation.
     */
    static float distalBlendWeight(
            SkeletonVec3 position,
            SkeletonVec3 start,
            SkeletonVec3 bend,
            SkeletonVec3 end) {
        SkeletonVec3 axis = end.subtract(start);
        float denominator = axis.lengthSquared();
        if (denominator <= EPSILON) {
            return 0.0F;
        }

        float t = dot(position.subtract(start), axis) / denominator;
        float bendT = clamp01(dot(bend.subtract(start), axis) / denominator);
        if (!Float.isFinite(t) || !Float.isFinite(bendT)) {
            return 0.0F;
        }

        float transitionHalfWidth = transitionHalfWidth(start, bend, end);
        float lowerEdge = bendT - transitionHalfWidth;
        float upperEdge = bendT + transitionHalfWidth;
        if (t <= lowerEdge) {
            return 0.0F;
        }
        if (t >= upperEdge) {
            return 1.0F;
        }
        return clamp01((t - lowerEdge) / Math.max(EPSILON, upperEdge - lowerEdge));
    }

    /**
     * Blend width is expressed in normalized chain coordinates. The old constant
     * 0.08 was already scale invariant; this preserves that property while making
     * the transition narrower for strongly uneven adjacent segment proportions.
     */
    static float transitionHalfWidth(SkeletonVec3 start, SkeletonVec3 bend, SkeletonVec3 end) {
        float upperLength = start.distanceTo(bend);
        float lowerLength = bend.distanceTo(end);
        float chainLength = upperLength + lowerLength;
        if (chainLength <= EPSILON) {
            return MIN_TRANSITION_HALF_WIDTH;
        }
        float shorterFraction = Math.min(upperLength, lowerLength) / chainLength;
        return clamp(
                shorterFraction * TRANSITION_SHORT_SEGMENT_FACTOR,
                MIN_TRANSITION_HALF_WIDTH,
                MAX_TRANSITION_HALF_WIDTH
        );
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
        return clamp(value, 0.0F, 1.0F);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
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
