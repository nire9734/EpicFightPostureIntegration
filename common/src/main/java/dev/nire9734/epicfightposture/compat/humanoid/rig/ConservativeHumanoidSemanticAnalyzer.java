package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Conservative framework-neutral humanoid semantic analyser.
 *
 * <p>This deliberately carries forward the strict graph checks used by the old
 * Gecko-only detector, while moving them above any particular model library.
 * Names identify candidate branches; hierarchy, geometry, and left/right layout
 * decide whether they are safe enough to normalize.</p>
 */
public final class ConservativeHumanoidSemanticAnalyzer implements HumanoidSemanticAnalyzer {
    public static final ConservativeHumanoidSemanticAnalyzer INSTANCE =
            new ConservativeHumanoidSemanticAnalyzer();

    private static final int MAX_HEAD_DISTANCE_FROM_BODY = 3;
    private static final int MAX_ARM_DISTANCE_FROM_BODY = 3;
    private static final int MAX_LEG_DISTANCE_FROM_BODY = 4;
    private static final int MAX_PAIR_DEPTH_DELTA = 2;
    private static final float GEOMETRY_CONFIDENCE = 0.72F;
    private static final float TORSO_ROOT_COLLAPSE_EPSILON = 1.0E-4F;

    private static final Set<String> BODY = aliases(
            "body", "torso", "chest", "upperbody", "upper_body", "trunk");
    private static final Set<String> PREFERRED_UPPER_TORSO = aliases(
            "torso", "chest", "upperbody", "upper_body", "trunk");
    private static final Set<String> HEAD = aliases("head", "skull");
    private static final Set<String> RIGHT_ARM = aliases(
            "rightarm", "right_arm", "armright", "arm_right", "rarm", "armr",
            "rightupperarm", "upperarmright", "upper_arm_right");
    private static final Set<String> LEFT_ARM = aliases(
            "leftarm", "left_arm", "armleft", "arm_left", "larm", "arml",
            "leftupperarm", "upperarmleft", "upper_arm_left");
    private static final Set<String> RIGHT_SHOULDER_CARRIER = aliases(
            "rightshoulder", "right_shoulder", "shoulderright", "shoulder_right", "rshoulder");
    private static final Set<String> LEFT_SHOULDER_CARRIER = aliases(
            "leftshoulder", "left_shoulder", "shoulderleft", "shoulder_left", "lshoulder");
    private static final Set<String> RIGHT_FOREARM = aliases(
            "rightforearm", "right_forearm", "forearmright", "forearm_right",
            "rightlowerarm", "right_lower_arm", "lowerarmright", "lower_arm_right",
            "rforearm");
    private static final Set<String> LEFT_FOREARM = aliases(
            "leftforearm", "left_forearm", "forearmleft", "forearm_left",
            "leftlowerarm", "left_lower_arm", "lowerarmleft", "lower_arm_left",
            "lforearm");
    private static final Set<String> RIGHT_HAND = aliases(
            "righthand", "right_hand", "handright", "hand_right", "rhand", "handr",
            "rightwrist", "right_wrist", "wristright", "wrist_right");
    private static final Set<String> LEFT_HAND = aliases(
            "lefthand", "left_hand", "handleft", "hand_left", "lhand", "handl",
            "leftwrist", "left_wrist", "wristleft", "wrist_left");
    private static final Set<String> RIGHT_LEG = aliases(
            "rightleg", "right_leg", "legright", "leg_right", "rleg",
            "rightthigh", "right_thigh", "thighright", "thigh_right");
    private static final Set<String> LEFT_LEG = aliases(
            "leftleg", "left_leg", "legleft", "leg_left", "lleg",
            "leftthigh", "left_thigh", "thighleft", "thigh_left");
    private static final Set<String> RIGHT_LOWER_LEG = aliases(
            "rightlowerleg", "right_lower_leg", "lowerlegright", "lower_leg_right",
            "rightshin", "right_shin", "shinright", "rshin", "rightcalf");
    private static final Set<String> LEFT_LOWER_LEG = aliases(
            "leftlowerleg", "left_lower_leg", "lowerlegleft", "lower_leg_left",
            "leftshin", "left_shin", "shinleft", "lshin", "leftcalf");
    private static final Set<String> RIGHT_FOOT = aliases(
            "rightfoot", "right_foot", "footright", "foot_right", "rfoot",
            "rightankle", "right_ankle", "ankleright", "ankle_right");
    private static final Set<String> LEFT_FOOT = aliases(
            "leftfoot", "left_foot", "footleft", "foot_left", "lfoot",
            "leftankle", "left_ankle", "ankleleft", "ankle_left");

    @Override
    public HumanoidSemanticAnalysis analyze(SourceRigSnapshot snapshot) {
        List<NodeCandidate> nodes = candidates(snapshot);

        SourceNodeSnapshot declaredHead = findBest(nodes, HEAD);
        SourceNodeSnapshot head = visibleHeadCarrier(snapshot, declaredHead);
        SourceNodeSnapshot rightArm = findBest(nodes, RIGHT_ARM);
        SourceNodeSnapshot leftArm = findBest(nodes, LEFT_ARM);
        SourceNodeSnapshot rightLeg = findBest(nodes, RIGHT_LEG);
        SourceNodeSnapshot leftLeg = findBest(nodes, LEFT_LEG);
        SourceNodeSnapshot body = findBestBody(
                snapshot,
                nodes,
                head,
                rightArm,
                leftArm,
                rightLeg,
                leftLeg
        );

        requireDistinct(body, head, rightArm, leftArm, rightLeg, leftLeg);
        requireSharedRoot(snapshot, body, head, rightArm, leftArm, rightLeg, leftLeg);
        requireCompactHierarchy(snapshot, body, head, rightArm, leftArm, rightLeg, leftLeg);
        requireIndependentBranches(snapshot, body, head, rightArm, leftArm, rightLeg, leftLeg);
        requireRenderableBranches(snapshot, head, rightArm, leftArm, rightLeg, leftLeg);

        // A number of authored humanoids insert a real shoulder/clavicle carrier
        // between the torso and the named upper-arm node. If it is an exact
        // shoulder alias on that ancestry path, use its authored pivot as the
        // semantic shoulder. This keeps visible shoulder plates and the upper arm
        // on the same EF deformation chain without guessing from a mod/entity id.
        SourceNodeSnapshot rightShoulder = findStructuralShoulderCarrier(
                snapshot, body, rightArm, RIGHT_SHOULDER_CARRIER);
        SourceNodeSnapshot leftShoulder = findStructuralShoulderCarrier(
                snapshot, body, leftArm, LEFT_SHOULDER_CARRIER);
        SourceNodeSnapshot rightShoulderSource = rightShoulder != null ? rightShoulder : rightArm;
        SourceNodeSnapshot leftShoulderSource = leftShoulder != null ? leftShoulder : leftArm;
        Set<String> rightShoulderNames = rightShoulder != null ? RIGHT_SHOULDER_CARRIER : RIGHT_ARM;
        Set<String> leftShoulderNames = leftShoulder != null ? LEFT_SHOULDER_CARRIER : LEFT_ARM;

        EnumMap<HumanoidJoint, SemanticJointCandidate> result = new EnumMap<>(HumanoidJoint.class);
        putSource(result, snapshot, HumanoidJoint.CHEST, body, confidence(body, BODY, true));
        // The visible head carrier remains a direct SOURCE owner so its mesh
        // receives head skin weights; only the semantic confidence is inferred.
        putSource(result, snapshot, HumanoidJoint.HEAD, head,
                head == declaredHead ? confidence(head, HEAD, false) : GEOMETRY_CONFIDENCE);
        putSource(result, snapshot, HumanoidJoint.SHOULDER_RIGHT, rightShoulderSource,
                confidence(rightShoulderSource, rightShoulderNames, false));
        putSource(result, snapshot, HumanoidJoint.SHOULDER_LEFT, leftShoulderSource,
                confidence(leftShoulderSource, leftShoulderNames, false));
        putSource(result, snapshot, HumanoidJoint.HIP_RIGHT, rightLeg, confidence(rightLeg, RIGHT_LEG, false));
        putSource(result, snapshot, HumanoidJoint.HIP_LEFT, leftLeg, confidence(leftLeg, LEFT_LEG, false));

        SourceNodeSnapshot rightForearm = findDescendantBest(snapshot, rightArm, RIGHT_FOREARM);
        SourceNodeSnapshot leftForearm = findDescendantBest(snapshot, leftArm, LEFT_FOREARM);
        SourceNodeSnapshot rightHand = findDescendantBest(snapshot, rightArm, RIGHT_HAND);
        SourceNodeSnapshot leftHand = findDescendantBest(snapshot, leftArm, LEFT_HAND);
        SourceNodeSnapshot rightLowerLeg = findDescendantBest(snapshot, rightLeg, RIGHT_LOWER_LEG);
        SourceNodeSnapshot leftLowerLeg = findDescendantBest(snapshot, leftLeg, LEFT_LOWER_LEG);
        SourceNodeSnapshot rightFoot = findDescendantBest(snapshot, rightLeg, RIGHT_FOOT);
        SourceNodeSnapshot leftFoot = findDescendantBest(snapshot, leftLeg, LEFT_FOOT);

        if (rightForearm != null) {
            putSource(result, snapshot, HumanoidJoint.ELBOW_RIGHT, rightForearm,
                    confidence(rightForearm, RIGHT_FOREARM, false));
        }
        if (leftForearm != null) {
            putSource(result, snapshot, HumanoidJoint.ELBOW_LEFT, leftForearm,
                    confidence(leftForearm, LEFT_FOREARM, false));
        }
        if (rightLowerLeg != null) {
            putSource(result, snapshot, HumanoidJoint.KNEE_RIGHT, rightLowerLeg,
                    confidence(rightLowerLeg, RIGHT_LOWER_LEG, false));
        }
        if (leftLowerLeg != null) {
            putSource(result, snapshot, HumanoidJoint.KNEE_LEFT, leftLowerLeg,
                    confidence(leftLowerLeg, LEFT_LOWER_LEG, false));
        }

        if (rightHand != null) {
            putSource(result, snapshot, HumanoidJoint.WRIST_RIGHT, rightHand,
                    confidence(rightHand, RIGHT_HAND, false));
        } else {
            inferTerminalFromGeometry(result, snapshot, HumanoidJoint.WRIST_RIGHT,
                    rightForearm != null ? rightForearm : rightArm,
                    rightForearm != null ? rightForearm : rightArm);
        }

        if (leftHand != null) {
            putSource(result, snapshot, HumanoidJoint.WRIST_LEFT, leftHand,
                    confidence(leftHand, LEFT_HAND, false));
        } else {
            inferTerminalFromGeometry(result, snapshot, HumanoidJoint.WRIST_LEFT,
                    leftForearm != null ? leftForearm : leftArm,
                    leftForearm != null ? leftForearm : leftArm);
        }

        if (rightFoot != null) {
            putSource(result, snapshot, HumanoidJoint.ANKLE_RIGHT, rightFoot,
                    confidence(rightFoot, RIGHT_FOOT, false));
        } else {
            inferTerminalFromGeometry(result, snapshot, HumanoidJoint.ANKLE_RIGHT,
                    rightLowerLeg != null ? rightLowerLeg : rightLeg,
                    rightLowerLeg != null ? rightLowerLeg : rightLeg);
        }

        if (leftFoot != null) {
            putSource(result, snapshot, HumanoidJoint.ANKLE_LEFT, leftFoot,
                    confidence(leftFoot, LEFT_FOOT, false));
        } else {
            inferTerminalFromGeometry(result, snapshot, HumanoidJoint.ANKLE_LEFT,
                    leftLowerLeg != null ? leftLowerLeg : leftLeg,
                    leftLowerLeg != null ? leftLowerLeg : leftLeg);
        }

        return new HumanoidSemanticAnalysis(result);
    }

    private static void putSource(
            Map<HumanoidJoint, SemanticJointCandidate> result,
            SourceRigSnapshot snapshot,
            HumanoidJoint joint,
            SourceNodeSnapshot source,
            float confidence) {
        result.put(joint, SemanticJointCandidate.fromSource(
                snapshot, joint, source.id(), JointOrigin.SOURCE, confidence));
    }

    private static void inferTerminalFromGeometry(
            Map<HumanoidJoint, SemanticJointCandidate> result,
            SourceRigSnapshot snapshot,
            HumanoidJoint joint,
            SourceNodeSnapshot anchorNode,
            SourceNodeSnapshot branchRoot) {
        SkeletonVec3 anchor = anchorNode.bindModelTransform().position();
        Optional<SkeletonVec3> terminal = HumanoidGeometryInference.inferDistalCenter(
                snapshot, branchRoot.id(), anchor);
        if (terminal.isEmpty() || terminal.orElseThrow().distanceTo(anchor) <= 1.0E-5F) {
            return;
        }

        result.put(joint, new SemanticJointCandidate(
                joint,
                terminal.orElseThrow(),
                Optional.of(branchRoot.id()),
                JointOrigin.INFERRED_FROM_GEOMETRY,
                GEOMETRY_CONFIDENCE
        ));
    }

    private static List<NodeCandidate> candidates(SourceRigSnapshot snapshot) {
        List<NodeCandidate> result = new ArrayList<>(snapshot.nodes().size());
        for (SourceNodeSnapshot node : snapshot.nodes().values()) {
            result.add(new NodeCandidate(node, snapshot.depth(node.id()), normalize(node.name())));
        }
        return result;
    }

    private static SourceNodeSnapshot findBestBody(
            SourceRigSnapshot snapshot,
            List<NodeCandidate> nodes,
            SourceNodeSnapshot head,
            SourceNodeSnapshot rightArm,
            SourceNodeSnapshot leftArm,
            SourceNodeSnapshot rightLeg,
            SourceNodeSnapshot leftLeg) {
        NodeCandidate best = null;
        int bestScore = Integer.MIN_VALUE;
        boolean tied = false;

        SkeletonVec3 hipMidpoint = null;
        if (rightLeg != null && leftLeg != null) {
            hipMidpoint = SkeletonVec3.lerp(
                    rightLeg.bindModelTransform().position(),
                    leftLeg.bindModelTransform().position(),
                    0.5F
            );
        }

        for (NodeCandidate candidate : nodes) {
            int lexical = score(candidate.normalizedName(), BODY);
            if (lexical <= 0) {
                continue;
            }

            int candidateScore = lexical - candidate.depth();
            if (candidate.node().geometryBounds().isPresent()) {
                candidateScore += 6;
            }
            if (PREFERRED_UPPER_TORSO.contains(candidate.normalizedName())) {
                candidateScore += 8;
            }

            candidateScore += torsoBranchScore(snapshot, candidate.node(), head);
            candidateScore += torsoBranchScore(snapshot, candidate.node(), rightArm);
            candidateScore += torsoBranchScore(snapshot, candidate.node(), leftArm);

            if (hipMidpoint != null
                    && candidate.node().bindModelTransform().position().distanceTo(hipMidpoint)
                    <= TORSO_ROOT_COLLAPSE_EPSILON) {
                candidateScore -= 80;
            }

            candidateScore += lowerBodyReachabilityScore(snapshot, candidate.node(), rightLeg);
            candidateScore += lowerBodyReachabilityScore(snapshot, candidate.node(), leftLeg);

            if (candidateScore > bestScore) {
                bestScore = candidateScore;
                best = candidate;
                tied = false;
            } else if (candidateScore == bestScore) {
                tied = true;
            }
        }

        return bestScore >= 70 && !tied && best != null ? best.node() : null;
    }

    private static int torsoBranchScore(
            SourceRigSnapshot snapshot,
            SourceNodeSnapshot torso,
            SourceNodeSnapshot branch) {
        if (torso == null || branch == null) {
            return 0;
        }
        int distance = graphDistance(snapshot, torso.id(), branch.id());
        if (distance < 0) {
            return -20;
        }

        if (snapshot.isAncestor(torso.id(), branch.id())) {
            return Math.max(0, 30 - distance * 6);
        }
        return Math.max(-8, 8 - distance * 3);
    }

    private static int lowerBodyReachabilityScore(
            SourceRigSnapshot snapshot,
            SourceNodeSnapshot torso,
            SourceNodeSnapshot leg) {
        if (torso == null || leg == null) {
            return 0;
        }
        int distance = graphDistance(snapshot, torso.id(), leg.id());
        if (distance < 0) {
            return -20;
        }
        return distance <= MAX_LEG_DISTANCE_FROM_BODY ? 4 : -12;
    }

    /**
     * Some authored models retain an empty `head` animation marker while the
     * actual head mesh is a sibling named `helmet` (e.g. Cataclysm's Coral Golem).
     * Only recover a unique, directly adjacent, geometric carrier; a real
     * renderable head always takes precedence, and ambiguous rigs still fail.
     */
    private static SourceNodeSnapshot visibleHeadCarrier(
            SourceRigSnapshot snapshot,
            SourceNodeSnapshot declaredHead) {
        if (declaredHead == null
                || declaredHead.parentId().isEmpty()
                || hasRenderableGeometry(snapshot, declaredHead.id())) {
            return declaredHead;
        }

        SourceNodeSnapshot carrier = null;
        for (SourceNodeSnapshot sibling : snapshot.childrenOf(declaredHead.parentId().orElseThrow())) {
            if (!"helmet".equals(normalize(sibling.name()))
                    || sibling.geometryBounds().isEmpty()
                    || !hasRenderableGeometry(snapshot, sibling.id())) {
                continue;
            }
            if (carrier != null) {
                return declaredHead;
            }
            carrier = sibling;
        }
        return carrier != null ? carrier : declaredHead;
    }

    private static SourceNodeSnapshot findBest(List<NodeCandidate> nodes, Set<String> names) {
        NodeCandidate best = null;
        int bestScore = 0;
        boolean tied = false;
        for (NodeCandidate candidate : nodes) {
            int score = score(candidate.normalizedName(), names) - candidate.depth();
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                tied = false;
            } else if (score > 0 && score == bestScore) {
                tied = true;
            }
        }
        return bestScore >= 70 && !tied && best != null ? best.node() : null;
    }

    /**
     * Finds an authored shoulder/clavicle carrier only when it lies on the exact
     * torso -> named upper-arm ancestry path. This is deliberately stricter than
     * a global name search so decorative shoulder nodes elsewhere in a model do
     * not become deformation anchors.
     */
    private static SourceNodeSnapshot findStructuralShoulderCarrier(
            SourceRigSnapshot snapshot,
            SourceNodeSnapshot body,
            SourceNodeSnapshot arm,
            Set<String> shoulderNames) {
        if (body == null || arm == null || !snapshot.isAncestor(body.id(), arm.id())) {
            return null;
        }

        SourceNodeSnapshot cursor = arm;
        while (cursor.parentId().isPresent()) {
            SourceNodeSnapshot parent = snapshot.node(cursor.parentId().orElseThrow());
            if (parent.id().equals(body.id())) {
                return null;
            }
            if (shoulderNames.contains(normalize(parent.name()))) {
                return parent;
            }
            cursor = parent;
        }
        return null;
    }

    private static SourceNodeSnapshot findDescendantBest(
            SourceRigSnapshot snapshot,
            SourceNodeSnapshot parent,
            Set<String> names) {
        if (parent == null) {
            return null;
        }
        List<NodeCandidate> descendants = new ArrayList<>();
        collectDescendants(snapshot, parent.id(), 0, descendants);
        return findBest(descendants, names);
    }

    private static void collectDescendants(
            SourceRigSnapshot snapshot,
            String parentId,
            int depth,
            List<NodeCandidate> output) {
        for (SourceNodeSnapshot child : snapshot.childrenOf(parentId)) {
            output.add(new NodeCandidate(child, depth + 1, normalize(child.name())));
            collectDescendants(snapshot, child.id(), depth + 1, output);
        }
    }

    private static void requireDistinct(SourceNodeSnapshot... nodes) {
        Set<String> ids = new HashSet<>();
        for (SourceNodeSnapshot node : nodes) {
            if (node == null || !ids.add(node.id())) {
                throw new IllegalArgumentException(
                        "Major humanoid nodes are missing, duplicated, or ambiguous");
            }
        }
    }

    private static void requireSharedRoot(
            SourceRigSnapshot snapshot,
            SourceNodeSnapshot... nodes) {
        String root = null;
        for (SourceNodeSnapshot node : nodes) {
            String current = rootId(snapshot, node.id());
            if (root == null) {
                root = current;
            } else if (!root.equals(current)) {
                throw new IllegalArgumentException(
                        "Major humanoid nodes do not share one source skeleton root");
            }
        }
    }

    private static void requireCompactHierarchy(
            SourceRigSnapshot snapshot,
            SourceNodeSnapshot body,
            SourceNodeSnapshot head,
            SourceNodeSnapshot rightArm,
            SourceNodeSnapshot leftArm,
            SourceNodeSnapshot rightLeg,
            SourceNodeSnapshot leftLeg) {
        int headDistance = graphDistance(snapshot, body.id(), head.id());
        int rightArmDistance = graphDistance(snapshot, body.id(), rightArm.id());
        int leftArmDistance = graphDistance(snapshot, body.id(), leftArm.id());
        int rightLegDistance = graphDistance(snapshot, body.id(), rightLeg.id());
        int leftLegDistance = graphDistance(snapshot, body.id(), leftLeg.id());

        if (headDistance < 0 || headDistance > MAX_HEAD_DISTANCE_FROM_BODY) {
            throw new IllegalArgumentException("Head is too far from the body branch");
        }
        if (rightArmDistance < 0 || leftArmDistance < 0
                || rightArmDistance > MAX_ARM_DISTANCE_FROM_BODY
                || leftArmDistance > MAX_ARM_DISTANCE_FROM_BODY) {
            throw new IllegalArgumentException("Arm branches are too far from the body branch");
        }
        if (rightLegDistance < 0 || leftLegDistance < 0
                || rightLegDistance > MAX_LEG_DISTANCE_FROM_BODY
                || leftLegDistance > MAX_LEG_DISTANCE_FROM_BODY) {
            throw new IllegalArgumentException("Leg branches are too far from the body branch");
        }

        if (Math.abs(snapshot.depth(rightArm.id()) - snapshot.depth(leftArm.id())) > MAX_PAIR_DEPTH_DELTA) {
            throw new IllegalArgumentException("Left/right arm hierarchy is strongly asymmetric");
        }
        if (Math.abs(snapshot.depth(rightLeg.id()) - snapshot.depth(leftLeg.id())) > MAX_PAIR_DEPTH_DELTA) {
            throw new IllegalArgumentException("Left/right leg hierarchy is strongly asymmetric");
        }
    }

    private static void requireIndependentBranches(
            SourceRigSnapshot snapshot,
            SourceNodeSnapshot body,
            SourceNodeSnapshot head,
            SourceNodeSnapshot rightArm,
            SourceNodeSnapshot leftArm,
            SourceNodeSnapshot rightLeg,
            SourceNodeSnapshot leftLeg) {
        SourceNodeSnapshot[] branches = {head, rightArm, leftArm, rightLeg, leftLeg};
        for (int i = 0; i < branches.length; i++) {
            if (snapshot.isAncestor(branches[i].id(), body.id())) {
                throw new IllegalArgumentException(
                        "Body is nested under a head/arm/leg branch");
            }
            for (int j = i + 1; j < branches.length; j++) {
                if (snapshot.isAncestor(branches[i].id(), branches[j].id())
                        || snapshot.isAncestor(branches[j].id(), branches[i].id())) {
                    throw new IllegalArgumentException(
                            "A major head/arm/leg node is nested under another major branch");
                }
            }
        }
    }

    private static void requireRenderableBranches(
            SourceRigSnapshot snapshot,
            SourceNodeSnapshot head,
            SourceNodeSnapshot rightArm,
            SourceNodeSnapshot leftArm,
            SourceNodeSnapshot rightLeg,
            SourceNodeSnapshot leftLeg) {
        if (!hasRenderableGeometry(snapshot, head.id())) {
            throw new IllegalArgumentException("Head branch has no renderable geometry");
        }
        if (!hasRenderableGeometry(snapshot, rightArm.id())
                || !hasRenderableGeometry(snapshot, leftArm.id())) {
            throw new IllegalArgumentException("One or both arm branches have no renderable geometry");
        }
        if (!hasRenderableGeometry(snapshot, rightLeg.id())
                || !hasRenderableGeometry(snapshot, leftLeg.id())) {
            throw new IllegalArgumentException("One or both leg branches have no renderable geometry");
        }
    }

    private static boolean hasRenderableGeometry(SourceRigSnapshot snapshot, String nodeId) {
        if (HumanoidGeometryInference.isAttachmentBranch(snapshot, nodeId)) {
            return false;
        }
        SourceNodeSnapshot node = snapshot.node(nodeId);
        if (node.renderable()) {
            return true;
        }
        for (SourceNodeSnapshot child : snapshot.childrenOf(nodeId)) {
            if (hasRenderableGeometry(snapshot, child.id())) {
                return true;
            }
        }
        return false;
    }

    private static int graphDistance(SourceRigSnapshot snapshot, String firstId, String secondId) {
        if (firstId.equals(secondId)) {
            return 0;
        }
        Map<String, Integer> firstAncestors = new HashMap<>();
        String cursor = firstId;
        int distance = 0;
        while (cursor != null) {
            firstAncestors.put(cursor, distance++);
            cursor = snapshot.node(cursor).parentId().orElse(null);
        }

        cursor = secondId;
        distance = 0;
        while (cursor != null) {
            Integer firstDistance = firstAncestors.get(cursor);
            if (firstDistance != null) {
                return firstDistance + distance;
            }
            distance++;
            cursor = snapshot.node(cursor).parentId().orElse(null);
        }
        return -1;
    }

    private static String rootId(SourceRigSnapshot snapshot, String nodeId) {
        SourceNodeSnapshot cursor = snapshot.node(nodeId);
        while (cursor.parentId().isPresent()) {
            cursor = snapshot.node(cursor.parentId().orElseThrow());
        }
        return cursor.id();
    }

    private static float confidence(SourceNodeSnapshot node, Set<String> names, boolean body) {
        int value = score(normalize(node.name()), names);
        if (body && node.geometryBounds().isPresent()) {
            value += 20;
        }
        return Math.max(0.0F, Math.min(1.0F, value / 100.0F));
    }

    private static int score(String name, Set<String> names) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        int best = 0;
        for (String alias : names) {
            if (name.equals(alias)) {
                best = Math.max(best, 100);
            } else if (name.startsWith(alias) || name.endsWith(alias)) {
                best = Math.max(best, 82);
            }
        }
        return best;
    }

    private static Set<String> aliases(String... values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            result.add(normalize(value));
        }
        return Set.copyOf(result);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record NodeCandidate(SourceNodeSnapshot node, int depth, String normalizedName) {
    }

    private ConservativeHumanoidSemanticAnalyzer() {
    }
}
