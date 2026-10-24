package dev.nire9734.epicfightposture.neoforge.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureCoverage;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Diagnostic output only: merge renderer results with posture observations.
 */
public final class NeoForgeRendererCoverage {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void write(Map<ResourceLocation, String> results) {
        if (results == null) return;
        try {
            NeoForgePostureCoverage.replaceClientRendererScan(results);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.warn("EFPI could not record NeoForge renderer diagnostics", exception);
        }
    }

    private NeoForgeRendererCoverage() {}
}
