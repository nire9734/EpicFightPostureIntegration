package dev.nire9734.epicfightposture.compat.humanoid.client;

import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/** Loader-independent atomic coverage report output. Reports are diagnostics only. */
public final class RendererCoverageWriter {
    public static Path write(Path configDirectory, String loader, Map<ResourceLocation, String> results)
            throws IOException {
        Path directory = configDirectory.resolve("epicfight_posture_integration").resolve("cache");
        Files.createDirectories(directory);
        Path output = directory.resolve(loader + "-renderer-coverage.tsv");
        Path temporary = Files.createTempFile(directory, "renderer-coverage-", ".tmp");
        try {
            Files.writeString(temporary, RendererCoverageFormatter.format(loader, results),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
            return output;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private RendererCoverageWriter() { }
}
