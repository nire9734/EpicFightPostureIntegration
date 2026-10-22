package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-entity renderer diagnostics, separate from latest.log/debug.log.
 * The file describes the most recently completed client renderer scan; it
 * never grants permission for patch registration or server discovery.
 */
public final class ForgeRendererCoverage {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void write(Map<ResourceLocation, String> results) {
        if (results == null) return;
        Path directory = FMLPaths.CONFIGDIR.get()
                .resolve(EpicFightPostureIntegration.MODID).resolve("cache");
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "forge-coverage-", ".tmp");
            StringBuilder report = new StringBuilder("# EFPI Forge renderer coverage v1\n");
            report.append("# generated_at ").append(Instant.now()).append('\n');
            report.append("entity_id\tresult\n");
            Map<String, String> ordered = new TreeMap<>();
            for (var entry : results.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    ordered.put(entry.getKey().toString(), entry.getValue());
                }
            }
            ordered.forEach((id, result) -> report.append(id).append('\t')
                    .append(result.replace('\t', '_').replace('\n', '_')).append('\n'));
            Files.writeString(temporary, report.toString(), StandardCharsets.UTF_8);
            Path output = directory.resolve("forge-renderer-coverage.tsv");
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("EFPI could not write Forge renderer coverage report", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private ForgeRendererCoverage() { }
}
