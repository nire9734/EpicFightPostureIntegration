package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigFixtureCodec;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class RigFixtureCaptureTest {
    private static final String PROPERTY = "efpi.rigFixtureDirectory";

    @Test
    void optInCaptureSkipsDuplicatesButUpdatesChangedRigsAfterReload(@TempDir Path directory)
            throws IOException {
        String original = System.getProperty(PROPERTY);
        try {
            SourceRigSnapshot snapshot = fixture();
            System.clearProperty(PROPERTY);
            RigFixtureCapture.capture(snapshot);
            try (var files = Files.list(directory)) {
                assertEquals(0, files.count(), "Default configuration must not export fixtures");
            }

            System.setProperty(PROPERTY, directory.toString());
            RigFixtureCapture.capture(snapshot);
            Path file;
            try (var files = Files.list(directory)) {
                file = files.findFirst().orElseThrow();
            }
            assertTrue(file.getFileName().toString().endsWith(".rig.tsv"));
            assertEquals(SourceRigFixtureCodec.encode(snapshot), Files.readString(file));

            Files.writeString(file, "already exported", StandardCharsets.UTF_8);
            RigFixtureCapture.capture(snapshot);
            assertEquals("already exported", Files.readString(file),
                    "Repeated preparation of the same signature must not rewrite the fixture");

            // Resource reload may change authored bind geometry under the same model signature.
            var updatedNodes = new HashMap<>(snapshot.nodes());
            SourceNodeSnapshot body = snapshot.node("body");
            updatedNodes.put("body", new SourceNodeSnapshot(body.id(), body.name(),
                    body.parentId(), new RigidTransform(new SkeletonVec3(0, 1.3F, 0),
                    body.bindModelTransform().rotation()), body.geometryBounds(), body.renderable()));
            SourceRigSnapshot updated = new SourceRigSnapshot(snapshot.key(), updatedNodes,
                    snapshot.bounds(), snapshot.hadNonUniformBindScale());
            RigFixtureCapture.capture(updated);
            assertEquals(SourceRigFixtureCodec.encode(updated), Files.readString(file));
        } finally {
            if (original == null) {
                System.clearProperty(PROPERTY);
            } else {
                System.setProperty(PROPERTY, original);
            }
        }
    }

    private static SourceRigSnapshot fixture() throws IOException {
        try (InputStream stream = RigFixtureCaptureTest.class.getClassLoader()
                .getResourceAsStream("rig-fixtures/standard_humanoid.rig.tsv")) {
            assertNotNull(stream);
            return SourceRigFixtureCodec.decode(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
