package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SemanticJointCandidate;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelPartAlternateGeometryTest {
    @Test
    void excludesIndependentCombinedArmAlternativeAndItsSubtree() {
        Map<String, SourceNodeSnapshot> nodes = new LinkedHashMap<>();
        nodes.put("root", node("root", "root", null));
        nodes.put("body", node("body", "body", "root"));
        nodes.put("left_arm", node("left_arm", "left_arm", "root"));
        nodes.put("right_arm", node("right_arm", "right_arm", "root"));
        nodes.put("arms", node("arms", "arms", "root"));
        nodes.put("arms/left_shoulder", node("arms/left_shoulder", "left_shoulder", "arms"));

        SourceRigSnapshot snapshot = snapshot("test:alternate_arms", nodes);
        HumanoidSemanticAnalysis analysis = armAnalysis(snapshot, "left_arm", "right_arm");

        assertEquals(
                Set.of("arms", "arms/left_shoulder"),
                ModelPartAlternateGeometry.excludedGeometryNodes(snapshot, analysis)
        );
    }

    @Test
    void keepsAggregateContainerWhenSemanticArmsBelongToItsBranch() {
        Map<String, SourceNodeSnapshot> nodes = new LinkedHashMap<>();
        nodes.put("root", node("root", "root", null));
        nodes.put("body", node("body", "body", "root"));
        nodes.put("arms", node("arms", "arms", "root"));
        nodes.put("left_arm", node("left_arm", "left_arm", "arms"));
        nodes.put("right_arm", node("right_arm", "right_arm", "arms"));

        SourceRigSnapshot snapshot = snapshot("test:arm_container", nodes);
        HumanoidSemanticAnalysis analysis = armAnalysis(snapshot, "left_arm", "right_arm");

        assertTrue(ModelPartAlternateGeometry.excludedGeometryNodes(snapshot, analysis).isEmpty());
    }

    private static HumanoidSemanticAnalysis armAnalysis(
            SourceRigSnapshot snapshot,
            String leftArm,
            String rightArm) {
        EnumMap<HumanoidJoint, SemanticJointCandidate> candidates = new EnumMap<>(HumanoidJoint.class);
        candidates.put(HumanoidJoint.SHOULDER_LEFT, SemanticJointCandidate.fromSource(
                snapshot,
                HumanoidJoint.SHOULDER_LEFT,
                leftArm,
                JointOrigin.SOURCE,
                1.0F
        ));
        candidates.put(HumanoidJoint.SHOULDER_RIGHT, SemanticJointCandidate.fromSource(
                snapshot,
                HumanoidJoint.SHOULDER_RIGHT,
                rightArm,
                JointOrigin.SOURCE,
                1.0F
        ));
        return new HumanoidSemanticAnalysis(candidates);
    }

    private static SourceRigSnapshot snapshot(
            String key,
            Map<String, SourceNodeSnapshot> nodes) {
        return new SourceRigSnapshot(
                new SkeletonKey(key, "modelpart-alternate-test"),
                nodes,
                new BodyBounds(
                        new SkeletonVec3(-1.0F, -1.0F, -1.0F),
                        new SkeletonVec3(1.0F, 1.0F, 1.0F)
                ),
                false
        );
    }

    private static SourceNodeSnapshot node(String id, String name, String parentId) {
        return new SourceNodeSnapshot(
                id,
                name,
                parentId,
                new RigidTransform(SkeletonVec3.ZERO, SkeletonQuat.IDENTITY),
                new BodyBounds(
                        new SkeletonVec3(-0.1F, -0.1F, -0.1F),
                        new SkeletonVec3(0.1F, 0.1F, 0.1F)
                ),
                true
        );
    }
}
