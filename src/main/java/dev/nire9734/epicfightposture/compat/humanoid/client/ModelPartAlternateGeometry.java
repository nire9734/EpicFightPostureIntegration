package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Detects mutually-exclusive ModelPart geometry that duplicates already-resolved
 * humanoid limbs.
 *
 * <p>Some Minecraft models keep an aggregate pose mesh alongside the ordinary
 * independent limbs. IllagerModel, for example, owns both left/right arm parts
 * and a separate {@code arms} branch used only for the crossed-arm pose. Baking
 * both into the always-visible Epic Fight mesh leaves the aggregate branch rigid
 * while the real arms animate.</p>
 *
 * <p>This detector is structural rather than entity-ID based. A combined-arm
 * branch is excluded only when independent semantic left and right arms already
 * exist and the aggregate branch is outside both of those deformation branches.</p>
 */
final class ModelPartAlternateGeometry {
    private static final Set<String> COMBINED_ARM_NAMES = Set.of(
            "arms",
            "crossedarms",
            "foldedarms",
            "joinedarms",
            "combinedarms",
            "alternatearms"
    );

    static Set<String> excludedGeometryNodes(
            SourceRigSnapshot snapshot,
            HumanoidSemanticAnalysis analysis) {
        String leftArm = directSourceNode(analysis, HumanoidJoint.SHOULDER_LEFT);
        String rightArm = directSourceNode(analysis, HumanoidJoint.SHOULDER_RIGHT);
        if (leftArm == null || rightArm == null || leftArm.equals(rightArm)) {
            return Set.of();
        }

        Set<String> alternateRoots = new HashSet<>();
        for (SourceNodeSnapshot node : snapshot.nodes().values()) {
            if (!COMBINED_ARM_NAMES.contains(normalize(node.semanticName()))) {
                continue;
            }
            if (overlapsSemanticBranch(snapshot, node.id(), leftArm)
                    || overlapsSemanticBranch(snapshot, node.id(), rightArm)) {
                continue;
            }
            alternateRoots.add(node.id());
        }
        if (alternateRoots.isEmpty()) {
            return Set.of();
        }

        Set<String> excluded = new HashSet<>();
        for (SourceNodeSnapshot node : snapshot.nodes().values()) {
            for (String rootId : alternateRoots) {
                if (rootId.equals(node.id()) || snapshot.isAncestor(rootId, node.id())) {
                    excluded.add(node.id());
                    break;
                }
            }
        }
        return Set.copyOf(excluded);
    }

    private static String directSourceNode(
            HumanoidSemanticAnalysis analysis,
            HumanoidJoint joint) {
        return analysis.candidate(joint)
                .filter(candidate -> candidate.origin() == JointOrigin.SOURCE)
                .flatMap(candidate -> candidate.sourceNodeId())
                .orElse(null);
    }

    private static boolean overlapsSemanticBranch(
            SourceRigSnapshot snapshot,
            String aggregateId,
            String semanticId) {
        return aggregateId.equals(semanticId)
                || snapshot.isAncestor(aggregateId, semanticId)
                || snapshot.isAncestor(semanticId, aggregateId);
    }

    private static String normalize(String name) {
        return name == null
                ? ""
                : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private ModelPartAlternateGeometry() {
    }
}
