package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidTopology;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Golden acceptance/rejection and bind-joint regression fixtures shared by both loader builds. */
class SourceRigFixtureRegressionTest {
    @Test
    void standardHumanoidKeepsCanonicalAnchors() throws IOException {
        verifyAccepted("standard_humanoid", Map.of(
                HumanoidJoint.CHEST, "body",
                HumanoidJoint.HEAD, "head",
                HumanoidJoint.SHOULDER_RIGHT, "right_arm",
                HumanoidJoint.SHOULDER_LEFT, "left_arm",
                HumanoidJoint.HIP_RIGHT, "right_leg",
                HumanoidJoint.HIP_LEFT, "left_leg"
        ));
    }

    @Test
    void shoulderCarrierRigKeepsAuthoredShoulderAnchors() throws IOException {
        verifyAccepted("shoulder_carriers", Map.of(
                HumanoidJoint.CHEST, "body",
                HumanoidJoint.HEAD, "head",
                HumanoidJoint.SHOULDER_RIGHT, "right_shoulder",
                HumanoidJoint.SHOULDER_LEFT, "left_shoulder",
                HumanoidJoint.HIP_RIGHT, "right_leg",
                HumanoidJoint.HIP_LEFT, "left_leg"
        ));
    }

    @Test
    void emptyHeadWithVisibleHelmetPreservesHeadSkinning() throws IOException {
        SourceRigSnapshot snapshot = fixture("empty_head_helmet");
        verifyAccepted(snapshot, Map.of(
                HumanoidJoint.CHEST, "upper_body",
                HumanoidJoint.HEAD, "helmet",
                HumanoidJoint.SHOULDER_RIGHT, "right_arm",
                HumanoidJoint.SHOULDER_LEFT, "left_arm",
                HumanoidJoint.HIP_RIGHT, "right_leg",
                HumanoidJoint.HIP_LEFT, "left_leg"
        ));
        HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(snapshot);
        var descriptor = HumanoidSkeletonResolver.resolve(snapshot, analysis);
        assertEquals(JointOrigin.SOURCE, analysis.candidate(HumanoidJoint.HEAD).orElseThrow().origin());
        var weights = new HumanoidSkinWeightResolver(snapshot, analysis, descriptor)
                .weights("helmet", new SkeletonVec3(0.0F, 2.3F, 0.0F));
        assertEquals(HumanoidSkinWeightResolver.HEAD, weights.firstJoint());
        assertEquals(1, weights.count());
    }

    @Test
    void structurallyIncompleteOrNonHumanoidRigsStayRejected() throws IOException {
        for (String fixture : new String[]{"missing_arm", "empty_head", "quadruped"}) {
            SourceRigSnapshot snapshot = fixture(fixture);
            assertThrows(IllegalArgumentException.class, () -> {
                HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(snapshot);
                HumanoidSkeletonResolver.resolve(snapshot, analysis);
            }, fixture + " must not become an automatically patched humanoid");
        }
    }

    @Test
    void fixtureCodecRoundTripsAllBindDataWithoutPlatformDependencies() throws IOException {
        for (String name : new String[]{"standard_humanoid", "shoulder_carriers", "empty_head_helmet",
                "missing_arm", "empty_head", "quadruped"}) {
            SourceRigSnapshot original = fixture(name);
            String serialized = SourceRigFixtureCodec.encode(original);
            SourceRigSnapshot restored = SourceRigFixtureCodec.decode(serialized);
            assertEquals(serialized, SourceRigFixtureCodec.encode(restored), name);
            assertEquals(original.key(), restored.key(), name);
            assertEquals(original.bounds(), restored.bounds(), name);
            assertEquals(original.hadNonUniformBindScale(), restored.hadNonUniformBindScale(), name);
            assertEquals(original.nodes(), restored.nodes(), name);
        }
    }

    @Test
    void malformedFixtureNeverSilentlyChangesTheModel() throws IOException {
        String valid = SourceRigFixtureCodec.encode(fixture("standard_humanoid"));
        assertThrows(IllegalArgumentException.class,
                () -> SourceRigFixtureCodec.decode(valid.replace("source rig fixture v1", "source rig fixture v2")));
        assertThrows(IllegalArgumentException.class,
                () -> SourceRigFixtureCodec.decode(valid.replace("rig\tfixture:", "rig\t\\qfixture:")));
        assertThrows(IllegalArgumentException.class,
                () -> SourceRigFixtureCodec.decode(valid.replace("\t0.53\t", "\tNaN\t")));
    }

    private static void verifyAccepted(String fixture, Map<HumanoidJoint, String> expected) throws IOException {
        verifyAccepted(fixture(fixture), expected);
    }

    private static void verifyAccepted(SourceRigSnapshot snapshot, Map<HumanoidJoint, String> expected)
            throws IOException {
        HumanoidSemanticAnalysis analysis = assertDoesNotThrow(
                () -> ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(snapshot), snapshot.key().entityTypeId());
        var descriptor = assertDoesNotThrow(
                () -> HumanoidSkeletonResolver.resolve(snapshot, analysis), snapshot.key().entityTypeId());
        for (Map.Entry<HumanoidJoint, String> mapping : expected.entrySet()) {
            var candidate = analysis.candidate(mapping.getKey()).orElseThrow();
            assertEquals(mapping.getValue(), candidate.sourceNodeId().orElseThrow(),
                    snapshot.key().entityTypeId() + " " + mapping.getKey());
        }
        // All canonical bind joints must exist, be finite and retain valid confidence.
        // This also exercises the virtual elbow/knee and geometric wrist/ankle paths.
        for (HumanoidJoint joint : HumanoidTopology.ORDER) {
            var position = descriptor.bindModelPosition(joint);
            assertTrue(Float.isFinite(position.x()) && Float.isFinite(position.y())
                    && Float.isFinite(position.z()), snapshot.key().entityTypeId() + " " + joint);
            assertTrue(descriptor.joint(joint).confidence() >= 0.0F
                    && descriptor.joint(joint).confidence() <= 1.0F,
                    snapshot.key().entityTypeId() + " " + joint);
        }
        verifyGoldenJointPositions(snapshot, descriptor);
        HumanoidSkinWeightResolver skinning = new HumanoidSkinWeightResolver(snapshot, analysis, descriptor);
        snapshot.nodes().values().forEach(node -> node.geometryBounds().ifPresent(bounds -> {
            var weights = skinning.weights(node.id(), bounds.center());
            assertTrue(weights.count() == 1 || weights.count() == 2);
            assertEquals(1.0F, weights.firstWeight() + weights.secondWeight(), 1.0E-5F,
                    snapshot.key().entityTypeId() + " " + node.id());
            assertTrue(weights.firstWeight() >= 0.0F && weights.secondWeight() >= 0.0F);
        }));
    }

    private static void verifyGoldenJointPositions(SourceRigSnapshot snapshot,
                                                    dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor descriptor)
            throws IOException {
        String resource = "rig-fixtures/" + snapshot.key().modelSignature() + ".joints.tsv";
        try (InputStream stream = SourceRigFixtureRegressionTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing expected bind-joint positions: " + resource);
            Set<HumanoidJoint> seen = new HashSet<>();
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                assertEquals(5, columns.length, resource);
                assertEquals("joint", columns[0], resource);
                HumanoidJoint joint = HumanoidJoint.valueOf(columns[1]);
                assertTrue(seen.add(joint), "Duplicate golden joint: " + joint);
                SkeletonVec3 position = descriptor.bindModelPosition(joint);
                assertEquals(Float.parseFloat(columns[2]), position.x(), 1.0E-4F, resource + " " + joint + " x");
                assertEquals(Float.parseFloat(columns[3]), position.y(), 1.0E-4F, resource + " " + joint + " y");
                assertEquals(Float.parseFloat(columns[4]), position.z(), 1.0E-4F, resource + " " + joint + " z");
            }
            assertEquals(Set.copyOf(HumanoidTopology.ORDER), seen, resource);
        }
    }

    private static SourceRigSnapshot fixture(String name) throws IOException {
        String resource = "rig-fixtures/" + name + ".rig.tsv";
        try (InputStream stream = SourceRigFixtureRegressionTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing shared rig fixture: " + resource);
            return SourceRigFixtureCodec.decode(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
