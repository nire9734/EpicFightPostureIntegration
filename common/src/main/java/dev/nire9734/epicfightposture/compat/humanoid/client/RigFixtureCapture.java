package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigFixtureCodec;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Explicitly opt-in developer fixture export for successfully captured source rigs.
 *
 * <p>Set {@code -Defpi.rigFixtureDirectory=/path/to/fixtures} on the game client's
 * JVM to export the GeckoLib, Lionfish and ModelPart bind rigs encountered during
 * renderer preparation. Default behavior performs no I/O. Export failures never
 * influence compatibility registration, and repeated preparation of one model
 * signature does not repeatedly rewrite the same file. If a resource reload
 * changes the bind rig without changing its key, the fixture is updated.</p>
 */
public final class RigFixtureCapture {
    private static final Logger LOGGER = Logger.getLogger(RigFixtureCapture.class.getName());
    private static final Map<String, String> LAST_EXPORTED_CONTENT_HASH = new ConcurrentHashMap<>();
    private static final String PROPERTY = "efpi.rigFixtureDirectory";

    public static synchronized void capture(SourceRigSnapshot snapshot) {
        String directory = System.getProperty(PROPERTY);
        if (directory == null || directory.isBlank()) {
            return;
        }
        String identifier = snapshot.key().entityTypeId() + '\n' + snapshot.key().modelSignature();
        String cacheKey = directory + '\n' + identifier;
        try {
            String content = SourceRigFixtureCodec.encode(snapshot);
            String contentHash = digest(content);
            if (contentHash.equals(LAST_EXPORTED_CONTENT_HASH.get(cacheKey))) {
                return;
            }
            String prefix = snapshot.key().entityTypeId().replaceAll("[^a-zA-Z0-9_-]", "_");
            if (prefix.length() > 64) {
                prefix = prefix.substring(0, 64);
            }
            String hash = digest(identifier).substring(0, 24);
            Path folder = Path.of(directory);
            Files.createDirectories(folder);
            Path file = folder.resolve(prefix + "-" + hash + ".rig.tsv");
            Files.writeString(file, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LAST_EXPORTED_CONTENT_HASH.put(cacheKey, contentHash);
        } catch (IOException | RuntimeException | NoSuchAlgorithmException exception) {
            LOGGER.warning("EFPI could not export development rig fixture for "
                    + snapshot.key().entityTypeId() + ": " + exception);
        }
    }

    private static String digest(String input) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    private RigFixtureCapture() {
    }
}
