package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConservativeHumanoidSemanticAnalyzerTest {
    @Test
    void structuralShoulderCarrierBecomesProximalArmAnchor() {
        Map<String, SourceNodeSnapshot> nodes = new LinkedHashMap<>();
        nodes.put("root", node("root", "root", null, 0.0F, 0.0F, 0.0F, null));
        nodes.put("body", node("body", "body", "root", 0.0F, 1.25F, 0.0F,
                bounds(-0.35F, 0.8F, -0.2F, 0.35F, 1.65F, 0.2F)));
        nodes.put("head", node("head", "head", "body", 0.0F, 1.8F, 0.0F,
                bounds(-0.25F, 1.6F, -0.25F, 0.25F, 2.1F, 0.25F)));

        nodes.put("right_shoulder", node("right_shoulder", "right_shoulder", "body",
                0.42F, 1.5F, 0.0F,
                bounds(0.3F, 1.4F, -0.18F, 0.58F, 1.65F, 0.18F)));
        nodes.put("right_arm", node("right_arm", "right_arm", "right_shoulder",
                0.52F, 1.42F, 0.0F,
                bounds(0.45F, 0.85F, -0.12F, 0.65F, 1.45F, 0.12F)));
        nodes.put("right_forearm", node("right_forearm", "right_forearm", "right_arm",
                0.55F, 1.1F, 0.0F,
                bounds(0.48F, 0.5F, -0.12F, 0.68F, 1.1F, 0.12F)));

        nodes.put("left_shoulder", node("left_shoulder", "left_shoulder", "body",
                -0.42F, 1.5F, 0.0F,
                bounds(-0.58F, 1.4F, -0.18F, -0.3F, 1.65F, 0.18F)));
        nodes.put("left_arm", node("left_arm", "left_arm", "left_shoulder",
                -0.52F, 1.42F, 0.0F,
                bounds(-0.65F, 0.85F, -0.12F, -0.45F, 1.45F, 0.12F)));
        nodes.put("left_forearm", node("left_forearm", "left_forearm", "left_arm",
                -0.55F, 1.1F, 0.0F,
                bounds(-0.68F, 0.5F, -0.12F, -0.48F, 1.1F, 0.12F)));

        nodes.put("right_leg", node("right_leg", "right_leg", "root", 0.14F, 0.8F, 0.0F,
                bounds(0.02F, 0.0F, -0.12F, 0.28F, 0.8F, 0.12F)));
        nodes.put("left_leg", node("left_leg", "left_leg", "root", -0.14F, 0.8F, 0.0F,
                bounds(-0.28F, 0.0F, -0.12F, -0.02F, 0.8F, 0.12F)));

        SourceRigSnapshot snapshot = new SourceRigSnapshot(
                new SkeletonKey("test:shoulder_carrier", "shoulder-carrier"),
                nodes,
                bounds(-0.7F, 0.0F, -0.25F, 0.7F, 2.1F, 0.25F),
                false
        );

        HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(snapshot);

        // CHEST binding must come from semantics, not from a literal source bone name such as "torso".
        assertEquals("body", analysis.candidate(HumanoidJoint.CHEST)
                .orElseThrow().sourceNodeId().orElseThrow());
        assertEquals("right_shoulder", analysis.candidate(HumanoidJoint.SHOULDER_RIGHT)
                .orElseThrow().sourceNodeId().orElseThrow());
        assertEquals("left_shoulder", analysis.candidate(HumanoidJoint.SHOULDER_LEFT)
                .orElseThrow().sourceNodeId().orElseThrow());
        assertEquals("right_forearm", analysis.candidate(HumanoidJoint.ELBOW_RIGHT)
                .orElseThrow().sourceNodeId().orElseThrow());
        assertEquals("left_forearm", analysis.candidate(HumanoidJoint.ELBOW_LEFT)
                .orElseThrow().sourceNodeId().orElseThrow());
    }

    @Test
    void emptyCoralGolemHeadUsesVisibleSiblingHelmetAndSkinsItAsHead() {
        SourceRigSnapshot snapshot = coralGolemLikeSnapshot(true, false);
        HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(snapshot);

        assertEquals("helmet", analysis.candidate(HumanoidJoint.HEAD)
                .orElseThrow().sourceNodeId().orElseThrow());
        assertEquals(JointOrigin.SOURCE, analysis.candidate(HumanoidJoint.HEAD)
                .orElseThrow().origin());

        var descriptor = HumanoidSkeletonResolver.resolve(snapshot, analysis);
        var weights = new HumanoidSkinWeightResolver(snapshot, analysis, descriptor)
                .weights("helmet", new SkeletonVec3(0.0F, 2.3F, 0.0F));
        assertEquals(HumanoidSkinWeightResolver.HEAD, weights.firstJoint());
        assertEquals(1, weights.count());
    }

    @Test
    void visibleNamedHeadStillWinsOverHelmetSibling() {
        SourceRigSnapshot snapshot = coralGolemLikeSnapshot(true, true);
        HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(snapshot);
        assertEquals("head", analysis.candidate(HumanoidJoint.HEAD)
                .orElseThrow().sourceNodeId().orElseThrow());
    }

    @Test
    void emptyHeadWithoutUniqueVisibleHelmetRemainsUnsupported() {
        SourceRigSnapshot snapshot = coralGolemLikeSnapshot(false, false);
        assertEquals("Head branch has no renderable geometry",
                assertThrows(IllegalArgumentException.class,
                        () -> ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(snapshot))
                        .getMessage());
    }

    private static SourceRigSnapshot coralGolemLikeSnapshot(
            boolean helmetHasGeometry,
            boolean headHasGeometry) {
        Map<String, SourceNodeSnapshot> nodes = new LinkedHashMap<>();
        nodes.put("root", node("root", "root", null, 0.0F, 0.0F, 0.0F, null));
        nodes.put("lower_body", node("lower_body", "lower_body", "root", 0.0F, 0.75F, 0.0F,
                bounds(-0.35F, 0.5F, -0.25F, 0.35F, 1.0F, 0.25F)));
        nodes.put("upper_body", node("upper_body", "upper_body", "lower_body",
                0.0F, 1.3F, 0.0F, null));
        nodes.put("helmet", node("helmet", "helmet", "upper_body", 0.0F, 2.25F, 0.0F,
                helmetHasGeometry ? bounds(-0.3F, 1.95F, -0.25F, 0.3F, 2.6F, 0.25F) : null));
        nodes.put("chest", node("chest", "chest", "upper_body", 0.0F, 1.35F, 0.0F,
                bounds(-0.4F, 1.1F, -0.25F, 0.4F, 1.65F, 0.25F)));
        nodes.put("right_arm", node("right_arm", "right_arm", "chest", 0.55F, 1.5F, 0.0F,
                bounds(0.45F, 0.8F, -0.15F, 0.75F, 1.6F, 0.15F)));
        nodes.put("left_arm", node("left_arm", "left_arm", "chest", -0.55F, 1.5F, 0.0F,
                bounds(-0.75F, 0.8F, -0.15F, -0.45F, 1.6F, 0.15F)));
        nodes.put("head", node("head", "head", "upper_body", 0.0F, 1.5F, 0.0F,
                headHasGeometry ? bounds(-0.2F, 1.6F, -0.2F, 0.2F, 1.9F, 0.2F) : null));
        nodes.put("right_leg", node("right_leg", "right_leg", "root", 0.2F, 0.7F, 0.0F,
                bounds(0.05F, 0.0F, -0.15F, 0.35F, 0.8F, 0.15F)));
        nodes.put("left_leg", node("left_leg", "left_leg", "root", -0.2F, 0.7F, 0.0F,
                bounds(-0.35F, 0.0F, -0.15F, -0.05F, 0.8F, 0.15F)));

        return new SourceRigSnapshot(
                new SkeletonKey("cataclysm:coral_golem", "coral-golem-like"),
                nodes,
                bounds(-0.8F, 0.0F, -0.3F, 0.8F, 2.65F, 0.3F),
                false
        );
    }

    private static SourceNodeSnapshot node(
            String id,
            String name,
            String parent,
            float x,
            float y,
            float z,
            BodyBounds geometry) {
        return new SourceNodeSnapshot(
                id,
                name,
                parent,
                new RigidTransform(new SkeletonVec3(x, y, z), SkeletonQuat.IDENTITY),
                geometry,
                geometry != null
        );
    }

    private static BodyBounds bounds(
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ) {
        return new BodyBounds(
                new SkeletonVec3(minX, minY, minZ),
                new SkeletonVec3(maxX, maxY, maxZ)
        );
    }
}
