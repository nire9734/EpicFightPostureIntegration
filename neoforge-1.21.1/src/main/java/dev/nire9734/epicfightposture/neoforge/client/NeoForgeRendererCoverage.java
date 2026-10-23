package dev.nire9734.epicfightposture.neoforge.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.client.RendererCoverageWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
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
        try {
            Path output = RendererCoverageWriter.write(FMLPaths.CONFIGDIR.get(), "neoforge", results);
            LOGGER.info("EFPI renderer coverage: {} entities, {}", results.size(), output.toAbsolutePath());
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("EFPI could not write renderer coverage report", exception);
        }
    }

    private NeoForgeRendererCoverage() {}
}
