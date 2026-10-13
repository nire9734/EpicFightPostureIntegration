package dev.nire9734.epicfightposture.compat.humanoid.client;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Conservative topology validation for automatically detected Gecko humanoids.
 *
 * <p>Bone names are only the first gate. A candidate must also look like a
 * compact biped graph: one shared skeleton root, head/arms/legs close to the
 * body, roughly symmetric left/right hierarchy depths, independent major limb
 * branches, and visible geometry somewhere below every major appendage.</p>
 */
final class GeckoHumanoidTopologyValidator {
    private static final int MAX_HEAD_DISTANCE_FROM_BODY = 3;
    private static final int MAX_ARM_DISTANCE_FROM_BODY = 3;
    private static final int MAX_LEG_DISTANCE_FROM_BODY = 4;
    private static final int MAX_PAIR_DEPTH_DELTA = 2;
    private static final float GEOMETRY_EPSILON = 1.0E-4F;

    private static final Set<String> BODY = aliases(
            "body", "torso", "chest", "upperbody", "upper_body", "trunk");
    private static final Set<String> HEAD = aliases("head", "skull");
    private static final Set<String> RIGHT_ARM = aliases(
            "rightarm", "right_arm", "armright", "arm_right", "rarm", "armr",
            "rightupperarm", "upperarmright", "upper_arm_right");
    private static final Set<String> LEFT_ARM = aliases(
            "leftarm", "left_arm", "armleft", "arm_left", "larm", "arml",
            "leftupperarm", "upperarmleft", "upper_arm_left");
    private static final Set<String> RIGHT_LEG = aliases(
            "rightleg", "right_leg", "legright", "leg_right", "rleg",
            "rightthigh", "right_thigh", "thighright", "thigh_right");
    private static final Set<String> LEFT_LEG = aliases(
            "leftleg", "left_leg", "legleft", "leg_left", "lleg",
            "leftthigh", "left_thigh", "thighleft", "thigh_left");

    static ValidationResult validate(BakedGeoModel model) {
        if (model == null || model.topLevelBones() == null || model.topLevelBones().isEmpty()) {
            return ValidationResult.rejected("model has no bone hierarchy");
        }

        List<BoneCandidate> bones = new ArrayList<>();
        for (GeoBone root : model.topLevelBones()) {
            collect(root, 0, bones, new HashSet<>());
        }

        GeoBone body = findBestBody(bones);
        GeoBone head = findBest(bones, HEAD);
        GeoBone rightArm = findBest(bones, RIGHT_ARM);
        GeoBone leftArm = findBest(bones, LEFT_ARM);
        GeoBone rightLeg = findBest(bones, RIGHT_LEG);
        GeoBone leftLeg = findBest(bones, LEFT_LEG);

        if (!allDistinct(body, head, rightArm, leftArm, rightLeg, leftLeg)) {
            return ValidationResult.rejected("major humanoid bones are missing, duplicated, or ambiguous");
        }

        GeoBone root = topAncestor(body);
        if (root == null
                || topAncestor(head) != root
                || topAncestor(rightArm) != root
                || topAncestor(leftArm) != root
                || topAncestor(rightLeg) != root
                || topAncestor(leftLeg) != root) {
            return ValidationResult.rejected("major humanoid bones do not share one skeleton root");
        }

        int headDistance = graphDistance(body, head);
        int rightArmDistance = graphDistance(body, rightArm);
        int leftArmDistance = graphDistance(body, leftArm);
        int rightLegDistance = graphDistance(body, rightLeg);
        int leftLegDistance = graphDistance(body, leftLeg);

        if (headDistance < 0 || headDistance > MAX_HEAD_DISTANCE_FROM_BODY) {
            return ValidationResult.rejected("head is too far from the body branch");
        }
        if (rightArmDistance < 0 || leftArmDistance < 0
                || rightArmDistance > MAX_ARM_DISTANCE_FROM_BODY
                || leftArmDistance > MAX_ARM_DISTANCE_FROM_BODY) {
            return ValidationResult.rejected("arm branches are too far from the body branch");
        }
        if (rightLegDistance < 0 || leftLegDistance < 0
                || rightLegDistance > MAX_LEG_DISTANCE_FROM_BODY
                || leftLegDistance > MAX_LEG_DISTANCE_FROM_BODY) {
            return ValidationResult.rejected("leg branches are too far from the body branch");
        }

        int rightArmDepth = depth(rightArm);
        int leftArmDepth = depth(leftArm);
        int rightLegDepth = depth(rightLeg);
        int leftLegDepth = depth(leftLeg);
        if (rightArmDepth < 0 || leftArmDepth < 0
                || Math.abs(rightArmDepth - leftArmDepth) > MAX_PAIR_DEPTH_DELTA) {
            return ValidationResult.rejected("left/right arm hierarchy is strongly asymmetric");
        }
        if (rightLegDepth < 0 || leftLegDepth < 0
                || Math.abs(rightLegDepth - leftLegDepth) > MAX_PAIR_DEPTH_DELTA) {
            return ValidationResult.rejected("left/right leg hierarchy is strongly asymmetric");
        }

        GeoBone[] independentBranches = {head, rightArm, leftArm, rightLeg, leftLeg};
        if (!areIndependentBranches(independentBranches)) {
            return ValidationResult.rejected("a major head/arm/leg bone is nested under another major branch");
        }
        for (GeoBone branch : independentBranches) {
            if (isAncestor(branch, body)) {
                return ValidationResult.rejected("body is nested under a head/arm/leg branch");
            }
        }

        if (!hasRenderableGeometry(head)) {
            return ValidationResult.rejected("head branch has no renderable geometry");
        }
        if (!hasRenderableGeometry(rightArm) || !hasRenderableGeometry(leftArm)) {
            return ValidationResult.rejected("one or both arm branches have no renderable geometry");
        }
        if (!hasRenderableGeometry(rightLeg) || !hasRenderableGeometry(leftLeg)) {
            return ValidationResult.rejected("one or both leg branches have no renderable geometry");
        }

        return ValidationResult.accepted();
    }

    private static void collect(
            GeoBone bone,
            int depth,
            List<BoneCandidate> out,
            Set<GeoBone> visited) {
        if (bone == null || !visited.add(bone)) {
            return;
        }
        out.add(new BoneCandidate(bone, depth, normalize(bone.getName())));
        if (bone.getChildBones() == null) {
            return;
        }
        for (GeoBone child : bone.getChildBones()) {
            collect(child, depth + 1, out, visited);
        }
    }

    private static GeoBone findBestBody(List<BoneCandidate> bones) {
        BoneCandidate best = null;
        int bestScore = 0;
        boolean tied = false;

        for (BoneCandidate candidate : bones) {
            int candidateScore = score(candidate.normalizedName(), BODY) - candidate.depth();
            if (candidate.bone().getCubes() != null && !candidate.bone().getCubes().isEmpty()) {
                candidateScore += 20;
            }

            if (candidateScore > bestScore) {
                bestScore = candidateScore;
                best = candidate;
                tied = false;
            } else if (candidateScore > 0 && candidateScore == bestScore) {
                tied = true;
            }
        }

        return bestScore >= 70 && !tied && best != null ? best.bone() : null;
    }

    private static GeoBone findBest(List<BoneCandidate> bones, Set<String> aliases) {
        BoneCandidate best = null;
        int bestScore = 0;
        boolean tied = false;

        for (BoneCandidate candidate : bones) {
            int candidateScore = score(candidate.normalizedName(), aliases) - candidate.depth();
            if (candidateScore > bestScore) {
                bestScore = candidateScore;
                best = candidate;
                tied = false;
            } else if (candidateScore > 0 && candidateScore == bestScore) {
                tied = true;
            }
        }

        return bestScore >= 70 && !tied && best != null ? best.bone() : null;
    }

    private static int score(String name, Set<String> aliases) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        int best = 0;
        for (String alias : aliases) {
            if (name.equals(alias)) {
                best = Math.max(best, 100);
            } else if (name.startsWith(alias) || name.endsWith(alias)) {
                best = Math.max(best, 82);
            }
        }
        return best;
    }

    private static boolean allDistinct(GeoBone... bones) {
        Set<GeoBone> unique = new HashSet<>();
        for (GeoBone bone : bones) {
            if (bone == null || !unique.add(bone)) {
                return false;
            }
        }
        return true;
    }

    private static boolean areIndependentBranches(GeoBone[] branches) {
        for (int i = 0; i < branches.length; i++) {
            for (int j = i + 1; j < branches.length; j++) {
                if (isAncestor(branches[i], branches[j]) || isAncestor(branches[j], branches[i])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isAncestor(GeoBone ancestor, GeoBone descendant) {
        if (ancestor == null || descendant == null || ancestor == descendant) {
            return false;
        }
        Set<GeoBone> visited = new HashSet<>();
        GeoBone cursor = descendant.getParent();
        while (cursor != null && visited.add(cursor)) {
            if (cursor == ancestor) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    private static int graphDistance(GeoBone first, GeoBone second) {
        if (first == null || second == null) {
            return -1;
        }
        if (first == second) {
            return 0;
        }

        Map<GeoBone, Integer> firstAncestors = new HashMap<>();
        Set<GeoBone> visited = new HashSet<>();
        GeoBone cursor = first;
        int distance = 0;
        while (cursor != null && visited.add(cursor)) {
            firstAncestors.put(cursor, distance++);
            cursor = cursor.getParent();
        }

        visited.clear();
        cursor = second;
        distance = 0;
        while (cursor != null && visited.add(cursor)) {
            Integer firstDistance = firstAncestors.get(cursor);
            if (firstDistance != null) {
                return firstDistance + distance;
            }
            distance++;
            cursor = cursor.getParent();
        }
        return -1;
    }

    private static int depth(GeoBone bone) {
        if (bone == null) {
            return -1;
        }
        Set<GeoBone> visited = new HashSet<>();
        GeoBone cursor = bone;
        int depth = 0;
        while (cursor != null && visited.add(cursor)) {
            GeoBone parent = cursor.getParent();
            if (parent == null) {
                return depth;
            }
            depth++;
            cursor = parent;
        }
        return -1;
    }

    private static GeoBone topAncestor(GeoBone bone) {
        if (bone == null) {
            return null;
        }
        Set<GeoBone> visited = new HashSet<>();
        GeoBone cursor = bone;
        GeoBone last = null;
        while (cursor != null && visited.add(cursor)) {
            last = cursor;
            cursor = cursor.getParent();
        }
        return cursor == null ? last : null;
    }

    private static boolean hasRenderableGeometry(GeoBone root) {
        return hasRenderableGeometry(root, new HashSet<>());
    }

    private static boolean hasRenderableGeometry(GeoBone bone, Set<GeoBone> visited) {
        if (bone == null || !visited.add(bone)) {
            return false;
        }

        if (bone.getCubes() != null) {
            for (GeoCube cube : bone.getCubes()) {
                if (hasNonZeroGeometry(cube)) {
                    return true;
                }
            }
        }

        if (bone.getChildBones() != null) {
            for (GeoBone child : bone.getChildBones()) {
                if (hasRenderableGeometry(child, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasNonZeroGeometry(GeoCube cube) {
        GeoQuad[] quads = cube == null ? null : cube.quads();
        if (quads == null || quads.length == 0) {
            return false;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        boolean foundVertex = false;

        for (GeoQuad quad : quads) {
            GeoVertex[] vertices = quad == null ? null : quad.vertices();
            if (vertices == null) {
                continue;
            }
            for (GeoVertex vertex : vertices) {
                if (vertex == null || vertex.position() == null) {
                    continue;
                }
                var position = vertex.position();
                float x = position.x();
                float y = position.y();
                float z = position.z();
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                    continue;
                }
                foundVertex = true;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
        }

        return foundVertex
                && (maxX - minX > GEOMETRY_EPSILON
                || maxY - minY > GEOMETRY_EPSILON
                || maxZ - minZ > GEOMETRY_EPSILON);
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

    record ValidationResult(boolean supported, String reason) {
        static ValidationResult accepted() {
            return new ValidationResult(true, "ok");
        }

        static ValidationResult rejected(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    private record BoneCandidate(GeoBone bone, int depth, String normalizedName) {
    }

    private GeckoHumanoidTopologyValidator() {
    }
}
