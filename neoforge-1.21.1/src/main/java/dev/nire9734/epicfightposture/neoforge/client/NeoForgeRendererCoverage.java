package dev.nire9734.epicfightposture.neoforge.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Diagnostic output only: never used to authorize Mob patches or networking.
 * The file is overwritten on each successful renderer-registration scan.
 */
public final class NeoForgeRendererCoverage {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void write(Map<ResourceLocation, String> results) {
        if (results == null) return;
        Path dir = FMLPaths.CONFIGDIR.get()
                .resolve("epicfight_posture_integration").resolve("cache");
        Path temporary = null;
        try {
            Files.createDirectories(dir);
            Path output = dir.resolve("neoforge-renderer-coverage.tsv");
            temporary = Files.createTempFile(dir, "coverage-", ".tmp");
            StringBuilder text = new StringBuilder("# EFPI NeoForge renderer coverage v1\n");
            text.append("entity_id\tresult\n");
            Map<String, String> rows = new TreeMap<>();
            for (var row : results.entrySet()) {
                if (row.getKey() != null && row.getValue() != null)
                    rows.put(row.getKey().toString(), row.getValue());
            }
            rows.forEach((id, result) -> text.append(id).append('\t')
                    .append(result.replace('\t', '_').replace('\n', '_')).append('\n'));
            Files.writeString(temporary, text.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            LOGGER.info("EFPI renderer coverage: {} entities, {}", rows.size(), output.toAbsolutePath());
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("EFPI could not write renderer coverage report", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
            }
        }
    }

    private NeoForgeRendererCoverage() {}
}
