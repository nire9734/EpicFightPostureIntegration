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
 *
 * <p>The static semantic helpers are also the canonical weighting contract for
 * deformable secondary geometry such as armor. This keeps body and attachment
 * meshes on one set of Root/Chest and limb transition rules.</p>
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
            // Explicit segmented source limbs stay rigid per source segment. A
            // one-bone limb uses the same semanticWeights helper as secondary
            // meshes so their inferred bend transition cannot drift apart.
            case SHOULDER_RIGHT -> explicitRightElbow
                    ? SkinWeights.rigid(ARM_RIGHT)
                    : semanticWeights(descriptor, owner, position);
            case SHOULDER_LEFT -> explicitLeftElbow
                    ? SkinWeights.rigid(ARM_LEFT)
                    : semanticWeights(descriptor, owner, position);
            case HIP_RIGHT -> explicitRightKnee
                    ? SkinWeights.rigid(THIGH_RIGHT)
                    : semanticWeights(descriptor, owner, position);
            case HIP_LEFT -> explicitLeftKnee
                    ? SkinWeights.rigid(THIGH_LEFT)
                    : semanticWeights(descriptor, owner, position);
            default -> semanticWeights(descriptor, owner, position);
        };
    }

    /**
     * Canonical skinning rule for geometry whose semantic owner is already known.
     * Secondary/deformable meshes should call this rather than duplicating the
     * torso or limb transition math.
     */
    public static SkinWeights semanticWeights(
            HumanoidSkeletonDescriptor descriptor,
            HumanoidJoint owner,
            SkeletonVec3 position) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(position, "position");

        return switch (owner) {
            case ROOT, SPINE, CHEST -> torsoWeights(descriptor, position);
            case NECK, HEAD -> SkinWeights.rigid(HEAD);

            case SHOULDER_RIGHT -> chainBlend(
                    descriptor,
                    position,
                    HumanoidJoint.SHOULDER_RIGHT,
                    HumanoidJoint.ELBOW_RIGHT,
                    HumanoidJoint.WRIST_RIGHT,
                    ARM_RIGHT,
                    HAND_RIGHT);
            case ELBOW_RIGHT, WRIST_RIGHT -> SkinWeights.rigid(HAND_RIGHT);

            case SHOULDER_LEFT -> chainBlend(
                    descriptor,
                    position,
                    HumanoidJoint.SHOULDER_LEFT,
                    HumanoidJoint.ELBOW_LEFT,
                    HumanoidJoint.WRIST_LEFT,
                    ARM_LEFT,
                    HAND_LEFT);
            case ELBOW_LEFT, WRIST_LEFT -> SkinWeights.rigid(HAND_LEFT);

            case HIP_RIGHT -> chainBlend(
                    descriptor,
                    position,
                    HumanoidJoint.HIP_RIGHT,
                    HumanoidJoint.KNEE_RIGHT,
                    HumanoidJoint.ANKLE_RIGHT,
                    THIGH_RIGHT,
                    LEG_RIGHT);
            case KNEE_RIGHT, ANKLE_RIGHT -> SkinWeights.rigid(LEG_RIGHT);

            case HIP_LEFT -> chainBlend(
                    descriptor,
                    position,
                    HumanoidJoint.HIP_LEFT,
                    HumanoidJoint.KNEE_LEFT,
                    HumanoidJoint.ANKLE_LEFT,
                    THIGH_LEFT,
                    LEG_LEFT);
            case KNEE_LEFT, ANKLE_LEFT -> SkinWeights.rigid(LEG_LEFT);
        };
    }

    /**
     * Rigid BIPED deformation joint corresponding to a semantic source anchor.
     * This is used by attachment bridges for bones that the shared semantic
     * analyzer identified directly; descendants without a direct semantic anchor
     * can still choose a joint from {@link #weights(String, SkeletonVec3)}.
     */
    public static int deformationJointId(HumanoidJoint owner) {
        Objects.requireNonNull(owner, "owner");
        return switch (owner) {
            case ROOT -> ROOT;
            case SPINE, CHEST -> CHEST;
            case NECK, HEAD -> HEAD;
            case SHOULDER_RIGHT -> ARM_RIGHT;
            case ELBOW_RIGHT, WRIST_RIGHT -> HAND_RIGHT;
            case SHOULDER_LEFT -> ARM_LEFT;
            case ELBOW_LEFT, WRIST_LEFT -> HAND_LEFT;
            case HIP_RIGHT -> THIGH_RIGHT;
            case KNEE_RIGHT, ANKLE_RIGHT -> LEG_RIGHT;
            case HIP_LEFT -> THIGH_LEFT;
            case KNEE_LEFT, ANKLE_LEFT -> LEG_LEFT;
        };
    }

    /**
     * Applies the canonical humanoid Root/Spine/Chest split to arbitrary torso
     * geometry already expressed in EFPI model space. Attachment renderers use
     * this to skin long coats/robes with exactly the same transition as the body
     * mesh instead of inventing a second waist heuristic.
     */
    public static SkinWeights torsoWeights(
            HumanoidSkeletonDescriptor descriptor,
            SkeletonVec3 position) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(position, "position");

        SkeletonVec3 start = descriptor.bindModelPosition(HumanoidJoint.ROOT);
        SkeletonVec3 bend = descriptor.bindModelPosition(HumanoidJoint.SPINE);
        SkeletonVec3 end = descriptor.bindModelPosition(HumanoidJoint.CHEST);
        float distalWeight = distalBlendWeight(position, start, bend, end);

        if (distalWeight <= EPSILON) {
            return SkinWeights.rigid(ROOT);
        }
        if (distalWeight >= 1.0F - EPSILON) {
            return SkinWeights.rigid(CHEST);
        }
        return SkinWeights.blend(ROOT, CHEST, 1.0F - distalWeight, distalWeight);
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

    private static SkinWeights chainBlend(
            HumanoidSkeletonDescriptor descriptor,
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
