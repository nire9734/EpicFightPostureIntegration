package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.sync.ForgePostureCoverage;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Fold client renderer observations into the unified diagnostics report.
 * Client observations never authorize server compatibility registration.
 */
public final class ForgeRendererCoverage {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void write(Map<ResourceLocation, String> results) {
        if (results == null) return;
        try {
            ForgePostureCoverage.replaceClientRendererScan(results);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.warn("EFPI could not record Forge renderer diagnostics", exception);
        }
    }

    private ForgeRendererCoverage() { }
}
