package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidTopology;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigQuality;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Completes and validates a semantic humanoid analysis into the canonical EFPI
 * humanoid skeleton.
 *
 * <p>The resolver intentionally does not know Gecko, Lionfish, Citadel, or Epic
 * Fight classes. Analyzers provide confidently identified anchor joints; this
 * class synthesizes safe interior joints and rejects structurally ambiguous rigs.</p>
 */
public final class HumanoidSkeletonResolver {
    private static final float EPSILON = 1.0E-5F;
    private static final float MIN_RECOVERABLE_LIMB_SPAN = EPSILON * 4.0F;
    private static final float VIRTUAL_CONFIDENCE_FACTOR = 0.85F;
    private static final float GEOMETRY_RECOVERY_CONFIDENCE = 0.72F;
    private static final float ASYMMETRY_THRESHOLD = 0.20F;

    public static HumanoidSkeletonDescriptor resolve(
            SourceRigSnapshot snapshot,
            HumanoidSemanticAnalysis analysis) {
        EnumMap<HumanoidJoint, SemanticJointCandidate> resolved = new EnumMap<>(HumanoidJoint.class);
        resolved.putAll(analysis.candidates());

        validateSourceReferences(snapshot, resolved);
        recoverCollapsedSourceJoints(snapshot, resolved);
        completeVirtualJoints(resolved);
        repairCollapsedInteriorJoints(resolved);

        List<HumanoidJoint> missing = HumanoidTopology.ORDER.stream()
                .filter(joint -> !resolved.containsKey(joint))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Humanoid analysis is missing required anchors/joints: " + missing);
        }

        validateTopology(resolved);

        EnumMap<HumanoidJoint, JointDescriptor> joints = new EnumMap<>(HumanoidJoint.class);
        for (HumanoidJoint joint : HumanoidTopology.ORDER) {
            SemanticJointCandidate candidate = resolved.get(joint);
            joints.put(joint, new JointDescriptor(
                    joint,
                    candidate.bindModelPosition(),
                    candidate.origin(),
                    candidate.confidence()
            ));
        }

        RigQuality quality = deriveQuality(snapshot, resolved);
        return new HumanoidSkeletonDescriptor(snapshot.key(), joints, snapshot.bounds(), quality);
    }

    private static void validateSourceReferences(
            SourceRigSnapshot snapshot,
            Map<HumanoidJoint, SemanticJointCandidate> candidates) {
        Map<String, HumanoidJoint> directOwners = new HashMap<>();

        for (SemanticJointCandidate candidate : candidates.values()) {
            candidate.sourceNodeId().ifPresent(sourceId -> {
                snapshot.node(sourceId);
                if (candidate.origin() == JointOrigin.SOURCE) {
                    HumanoidJoint previous = directOwners.putIfAbsent(sourceId, candidate.joint());
                    if (previous != null && previous != candidate.joint()) {
                        throw new IllegalArgumentException(
                                "Source node " + sourceId + " is directly assigned to both "
                                        + previous + " and " + candidate.joint());
                    }
                }
            });
        }
    }

    /**
     * Some exported rigs expose semantically named helper bones whose pivots are
     * intentionally shared with a parent joint. Recover only the collapsed
     * canonical position from shared bind geometry while keeping semantic source
     * ownership intact for skin weighting.
     */
    private static void recoverCollapsedSourceJoints(
            SourceRigSnapshot snapshot,
            EnumMap<HumanoidJoint, SemanticJointCandidate> joints) {
        recoverCollapsedHead(snapshot, joints);
        recoverCollapsedTorso(snapshot, joints);
        recoverCollapsedTerminal(snapshot, joints, HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.WRIST_LEFT);
        recoverCollapsedTerminal(snapshot, joints, HumanoidJoint.SHOULDER_RIGHT, HumanoidJoint.WRIST_RIGHT);
        recoverCollapsedTerminal(snapshot, joints, HumanoidJoint.HIP_LEFT, HumanoidJoint.ANKLE_LEFT);
        recoverCollapsedTerminal(snapshot, joints, HumanoidJoint.HIP_RIGHT, HumanoidJoint.ANKLE_RIGHT);
    }

    private static void recoverCollapsedHead(
            SourceRigSnapshot snapshot,
            EnumMap<HumanoidJoint, SemanticJointCandidate> joints) {
        SemanticJointCandidate chest = joints.get(HumanoidJoint.CHEST);
        SemanticJointCandidate head = joints.get(HumanoidJoint.HEAD);
        if (chest == null || head == null
                || chest.bindModelPosition().distanceTo(head.bindModelPosition()) > EPSILON
                || head.sourceNodeId().isEmpty()) {
            return;
        }

        String sourceId = head.sourceNodeId().orElseThrow();
        Optional<BodyBounds> bounds = HumanoidGeometryInference.subtreeBounds(snapshot, sourceId);
        if (bounds.isEmpty()) {
            return;
        }

        BodyBounds headBounds = bounds.orElseThrow();
        SkeletonVec3 sourcePivot = head.bindModelPosition();
        float recoveredY = (headBounds.min().y() + headBounds.max().y()) * 0.5F;
        if (recoveredY <= chest.bindModelPosition().y() + EPSILON) {
            recoveredY = headBounds.max().y();
        }
        if (recoveredY <= chest.bindModelPosition().y() + EPSILON) {
            return;
        }

        joints.put(HumanoidJoint.HEAD, new SemanticJointCandidate(
                HumanoidJoint.HEAD,
                new SkeletonVec3(sourcePivot.x(), recoveredY, sourcePivot.z()),
                Optional.of(sourceId),
                JointOrigin.INFERRED_FROM_GEOMETRY,
                Math.min(head.confidence(), GEOMETRY_RECOVERY_CONFIDENCE)
        ));
    }

    private static void recoverCollapsedTorso(
            SourceRigSnapshot snapshot,
            EnumMap<HumanoidJoint, SemanticJointCandidate> joints) {
        SemanticJointCandidate chest = joints.get(HumanoidJoint.CHEST);
        SemanticJointCandidate leftHip = joints.get(HumanoidJoint.HIP_LEFT);
        SemanticJointCandidate rightHip = joints.get(HumanoidJoint.HIP_RIGHT);
        if (chest == null || leftHip == null || rightHip == null || chest.sourceNodeId().isEmpty()) {
            return;
        }

        SkeletonVec3 inferredRoot = SkeletonVec3.lerp(
                leftHip.bindModelPosition(),
                rightHip.bindModelPosition(),
                0.5F
        );
        if (chest.bindModelPosition().distanceTo(inferredRoot) > EPSILON) {
            return;
        }

        String sourceId = chest.sourceNodeId().orElseThrow();
        Optional<BodyBounds> bounds = HumanoidGeometryInference.ownOrSubtreeBounds(snapshot, sourceId);
        if (bounds.isEmpty()) {
            return;
        }

        BodyBounds torsoBounds = bounds.orElseThrow();
        SkeletonVec3 sourcePivot = chest.bindModelPosition();
        float recoveredY = (torsoBounds.min().y() + torsoBounds.max().y()) * 0.5F;
        if (recoveredY <= inferredRoot.y() + EPSILON) {
            recoveredY = torsoBounds.max().y();
        }

        SemanticJointCandidate head = joints.get(HumanoidJoint.HEAD);
        if (head != null && recoveredY >= head.bindModelPosition().y() - EPSILON) {
            recoveredY = (inferredRoot.y() + head.bindModelPosition().y()) * 0.5F;
        }
        if (recoveredY <= inferredRoot.y() + EPSILON) {
            return;
        }

        joints.put(HumanoidJoint.CHEST, new SemanticJointCandidate(
                HumanoidJoint.CHEST,
                new SkeletonVec3(sourcePivot.x(), recoveredY, sourcePivot.z()),
                Optional.of(sourceId),
                JointOrigin.INFERRED_FROM_GEOMETRY,
                Math.min(chest.confidence(), GEOMETRY_RECOVERY_CONFIDENCE)
        ));
    }

    private static void recoverCollapsedTerminal(
            SourceRigSnapshot snapshot,
            EnumMap<HumanoidJoint, SemanticJointCandidate> joints,
            HumanoidJoint proximalJoint,
            HumanoidJoint terminalJoint) {
        SemanticJointCandidate proximal = joints.get(proximalJoint);
        SemanticJointCandidate terminal = joints.get(terminalJoint);
        if (proximal == null || terminal == null
                || proximal.bindModelPosition().distanceTo(terminal.bindModelPosition()) > MIN_RECOVERABLE_LIMB_SPAN) {
            return;
        }

        SkeletonVec3 anchor = proximal.bindModelPosition();
        SkeletonVec3 recovered = terminal.sourceNodeId()
                .flatMap(sourceId -> HumanoidGeometryInference.inferDistalCenter(snapshot, sourceId, anchor))
                .orElse(anchor);

        if (recovered.distanceTo(anchor) <= MIN_RECOVERABLE_LIMB_SPAN) {
            recovered = proximal.sourceNodeId()
                    .flatMap(sourceId -> HumanoidGeometryInference.inferDistalCenter(snapshot, sourceId, anchor))
                    .orElse(anchor);
        }

        if (recovered.distanceTo(anchor) <= MIN_RECOVERABLE_LIMB_SPAN) {
            return;
        }

        Optional<String> recoveredSource = terminal.sourceNodeId().isPresent()
                ? terminal.sourceNodeId()
                : proximal.sourceNodeId();
        joints.put(terminalJoint, new SemanticJointCandidate(
                terminalJoint,
                recovered,
                recoveredSource,
                JointOrigin.INFERRED_FROM_GEOMETRY,
                Math.min(terminal.confidence(), GEOMETRY_RECOVERY_CONFIDENCE)
        ));
    }

    private static void completeVirtualJoints(
            EnumMap<HumanoidJoint, SemanticJointCandidate> joints) {
        inferBetween(joints, HumanoidJoint.ROOT, HumanoidJoint.HIP_LEFT, HumanoidJoint.HIP_RIGHT, 0.5F);
        inferBetween(joints, HumanoidJoint.CHEST, HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.SHOULDER_RIGHT, 0.5F);
        inferBetween(joints, HumanoidJoint.SPINE, HumanoidJoint.ROOT, HumanoidJoint.CHEST, 0.5F);
        inferBetween(joints, HumanoidJoint.NECK, HumanoidJoint.CHEST, HumanoidJoint.HEAD, 0.5F);
        inferBetween(joints, HumanoidJoint.ELBOW_LEFT,
                HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.WRIST_LEFT, 0.5F);
        inferBetween(joints, HumanoidJoint.ELBOW_RIGHT,
                HumanoidJoint.SHOULDER_RIGHT, HumanoidJoint.WRIST_RIGHT, 0.5F);
        inferBetween(joints, HumanoidJoint.KNEE_LEFT,
                HumanoidJoint.HIP_LEFT, HumanoidJoint.ANKLE_LEFT, 0.5F);
        inferBetween(joints, HumanoidJoint.KNEE_RIGHT,
                HumanoidJoint.HIP_RIGHT, HumanoidJoint.ANKLE_RIGHT, 0.5F);
    }

    private static void repairCollapsedInteriorJoints(
            EnumMap<HumanoidJoint, SemanticJointCandidate> joints) {
        repairCollapsedInterior(joints,
                HumanoidJoint.ELBOW_LEFT, HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.WRIST_LEFT);
        repairCollapsedInterior(joints,
                HumanoidJoint.ELBOW_RIGHT, HumanoidJoint.SHOULDER_RIGHT, HumanoidJoint.WRIST_RIGHT);
        repairCollapsedInterior(joints,
                HumanoidJoint.KNEE_LEFT, HumanoidJoint.HIP_LEFT, HumanoidJoint.ANKLE_LEFT);
        repairCollapsedInterior(joints,
                HumanoidJoint.KNEE_RIGHT, HumanoidJoint.HIP_RIGHT, HumanoidJoint.ANKLE_RIGHT);
    }

    private static void repairCollapsedInterior(
            EnumMap<HumanoidJoint, SemanticJointCandidate> joints,
            HumanoidJoint interiorJoint,
            HumanoidJoint proximalJoint,
            HumanoidJoint terminalJoint) {
        SemanticJointCandidate interior = joints.get(interiorJoint);
        SemanticJointCandidate proximal = joints.get(proximalJoint);
        SemanticJointCandidate terminal = joints.get(terminalJoint);
        if (interior == null || proximal == null || terminal == null) {
            return;
        }

        SkeletonVec3 proximalPosition = proximal.bindModelPosition();
        SkeletonVec3 terminalPosition = terminal.bindModelPosition();
        if (proximalPosition.distanceTo(terminalPosition) <= EPSILON * 2.0F) {
            return;
        }

        SkeletonVec3 interiorPosition = interior.bindModelPosition();
        if (interiorPosition.distanceTo(proximalPosition) > EPSILON
                && interiorPosition.distanceTo(terminalPosition) > EPSILON) {
            return;
        }

        float confidence = Math.min(proximal.confidence(), terminal.confidence())
                * VIRTUAL_CONFIDENCE_FACTOR;
        joints.put(interiorJoint, SemanticJointCandidate.virtual(
                interiorJoint,
                SkeletonVec3.lerp(proximalPosition, terminalPosition, 0.5F),
                confidence
        ));
    }

    private static void inferBetween(
            EnumMap<HumanoidJoint, SemanticJointCandidate> joints,
            HumanoidJoint target,
            HumanoidJoint first,
            HumanoidJoint second,
            float alpha) {
        if (joints.containsKey(target)) {
            return;
        }
        SemanticJointCandidate a = joints.get(first);
        SemanticJointCandidate b = joints.get(second);
        if (a == null || b == null) {
            return;
        }

        float confidence = Math.min(a.confidence(), b.confidence()) * VIRTUAL_CONFIDENCE_FACTOR;
        joints.put(target, SemanticJointCandidate.virtual(
                target,
                SkeletonVec3.lerp(a.bindModelPosition(), b.bindModelPosition(), alpha),
                confidence
        ));
    }

    private static void validateTopology(Map<HumanoidJoint, SemanticJointCandidate> joints) {
        SkeletonVec3 root = position(joints, HumanoidJoint.ROOT);
        SkeletonVec3 head = position(joints, HumanoidJoint.HEAD);
        if (head.y() <= root.y() + EPSILON) {
            throw new IllegalArgumentException("Humanoid head must be above the pelvis/root in EFPI model space");
        }

        validateLeftRight(joints, HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.SHOULDER_RIGHT, "shoulders");
        validateLeftRight(joints, HumanoidJoint.HIP_LEFT, HumanoidJoint.HIP_RIGHT, "hips");

        requireSegment(joints, HumanoidJoint.ROOT, HumanoidJoint.CHEST);
        requireSegment(joints, HumanoidJoint.CHEST, HumanoidJoint.HEAD);
        requireSegment(joints, HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.ELBOW_LEFT);
        requireSegment(joints, HumanoidJoint.ELBOW_LEFT, HumanoidJoint.WRIST_LEFT);
        requireSegment(joints, HumanoidJoint.SHOULDER_RIGHT, HumanoidJoint.ELBOW_RIGHT);
        requireSegment(joints, HumanoidJoint.ELBOW_RIGHT, HumanoidJoint.WRIST_RIGHT);
        requireSegment(joints, HumanoidJoint.HIP_LEFT, HumanoidJoint.KNEE_LEFT);
        requireSegment(joints, HumanoidJoint.KNEE_LEFT, HumanoidJoint.ANKLE_LEFT);
        requireSegment(joints, HumanoidJoint.HIP_RIGHT, HumanoidJoint.KNEE_RIGHT);
        requireSegment(joints, HumanoidJoint.KNEE_RIGHT, HumanoidJoint.ANKLE_RIGHT);
    }

    private static void validateLeftRight(
            Map<HumanoidJoint, SemanticJointCandidate> joints,
            HumanoidJoint leftJoint,
            HumanoidJoint rightJoint,
            String label) {
        SkeletonVec3 left = position(joints, leftJoint);
        SkeletonVec3 right = position(joints, rightJoint);
        if (left.distanceTo(right) <= EPSILON) {
            throw new IllegalArgumentException("Humanoid " + label + " collapse to the same pivot");
        }
    }

    private static void requireSegment(
            Map<HumanoidJoint, SemanticJointCandidate> joints,
            HumanoidJoint first,
            HumanoidJoint second) {
        SemanticJointCandidate a = joints.get(first);
        SemanticJointCandidate b = joints.get(second);
        if (a == null || b == null) {
            throw new IllegalArgumentException("Missing semantic segment endpoint: " + first + " -> " + second);
        }
        if (a.bindModelPosition().distanceTo(b.bindModelPosition()) <= EPSILON) {
            throw new IllegalArgumentException(
                    "Humanoid segment has zero length: " + first + " -> " + second
                            + " [" + a.bindModelPosition() + ", " + a.origin()
                            + " -> " + b.bindModelPosition() + ", " + b.origin() + "]");
        }
    }

    private static RigQuality deriveQuality(
            SourceRigSnapshot snapshot,
            Map<HumanoidJoint, SemanticJointCandidate> joints) {
        int source = 0;
        int inferred = 0;
        int virtual = 0;
        float confidenceSum = 0.0F;

        for (SemanticJointCandidate candidate : joints.values()) {
            confidenceSum += candidate.confidence();
            switch (candidate.origin()) {
                case SOURCE -> source++;
                case INFERRED_FROM_HIERARCHY, INFERRED_FROM_GEOMETRY -> inferred++;
                case VIRTUAL -> virtual++;
            }
        }

        float overallConfidence = joints.isEmpty() ? 0.0F : confidenceSum / joints.size();
        boolean asymmetric = isAsymmetric(joints);
        return new RigQuality(
                overallConfidence,
                source,
                inferred,
                virtual,
                asymmetric,
                snapshot.hadNonUniformBindScale()
        );
    }

    private static boolean isAsymmetric(Map<HumanoidJoint, SemanticJointCandidate> joints) {
        List<float[]> pairs = new ArrayList<>();
        pairs.add(lengthPair(joints,
                HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.ELBOW_LEFT,
                HumanoidJoint.SHOULDER_RIGHT, HumanoidJoint.ELBOW_RIGHT));
        pairs.add(lengthPair(joints,
                HumanoidJoint.ELBOW_LEFT, HumanoidJoint.WRIST_LEFT,
                HumanoidJoint.ELBOW_RIGHT, HumanoidJoint.WRIST_RIGHT));
        pairs.add(lengthPair(joints,
                HumanoidJoint.HIP_LEFT, HumanoidJoint.KNEE_LEFT,
                HumanoidJoint.HIP_RIGHT, HumanoidJoint.KNEE_RIGHT));
        pairs.add(lengthPair(joints,
                HumanoidJoint.KNEE_LEFT, HumanoidJoint.ANKLE_LEFT,
                HumanoidJoint.KNEE_RIGHT, HumanoidJoint.ANKLE_RIGHT));

        for (float[] pair : pairs) {
            if (relativeDifference(pair[0], pair[1]) > ASYMMETRY_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private static float[] lengthPair(
            Map<HumanoidJoint, SemanticJointCandidate> joints,
            HumanoidJoint leftA,
            HumanoidJoint leftB,
            HumanoidJoint rightA,
            HumanoidJoint rightB) {
        return new float[] {
                position(joints, leftA).distanceTo(position(joints, leftB)),
                position(joints, rightA).distanceTo(position(joints, rightB))
        };
    }

    private static float relativeDifference(float first, float second) {
        float maximum = Math.max(first, second);
        return maximum <= EPSILON ? 0.0F : Math.abs(first - second) / maximum;
    }

    private static SkeletonVec3 position(
            Map<HumanoidJoint, SemanticJointCandidate> joints,
            HumanoidJoint joint) {
        SemanticJointCandidate candidate = joints.get(joint);
        if (candidate == null) {
            throw new IllegalArgumentException("Missing semantic joint " + joint);
        }
        return candidate.bindModelPosition();
    }

    private HumanoidSkeletonResolver() {
    }
}
