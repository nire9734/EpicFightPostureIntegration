package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
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
        try {
            RendererCoverageWriter.write(FMLPaths.CONFIGDIR.get(), "forge", results);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("EFPI could not write Forge renderer coverage report", exception);
        }
    }

    private ForgeRendererCoverage() { }
}
