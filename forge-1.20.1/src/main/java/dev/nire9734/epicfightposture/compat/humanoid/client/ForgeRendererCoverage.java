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
import java.util.Map;

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
            Files.writeString(temporary, RendererCoverageFormatter.format("forge", results),
                    StandardCharsets.UTF_8);
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
