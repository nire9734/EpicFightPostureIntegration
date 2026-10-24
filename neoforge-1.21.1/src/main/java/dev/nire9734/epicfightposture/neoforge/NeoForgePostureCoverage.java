package dev.nire9734.epicfightposture.neoforge;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.PostureCoverageReport;
import dev.nire9734.epicfightposture.PostureExecutionEligibility;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryReportProcessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;

/** Server-owned diagnostic report; no I/O takes place inside eligibility checks. */
public final class NeoForgePostureCoverage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PostureCoverageReport REPORT = new PostureCoverageReport("neoforge");
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(NeoForgePostureCoverage::onServerStarted);
        NeoForge.EVENT_BUS.addListener(NeoForgePostureCoverage::onServerTick);
        NeoForge.EVENT_BUS.addListener(NeoForgePostureCoverage::onServerStopped);
    }

    public static void observe(LivingEntity entity, LivingEntityPatch<?> patch,
                               PostureExecutionEligibility.Comparison comparison) {
        REPORT.observe(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                comparison.legacyEligible(), comparison.current().status().name(),
                comparison.current().reason().name(),
                patch == null ? "none" : patch.getClass().getName());
    }

    public static void observeGuardBreak(LivingEntity entity, boolean confirmed, String reason) {
        REPORT.observeGuardBreak(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), confirmed, reason);
    }

    public static void observeEligibilityFailure(LivingEntity entity, LivingEntityPatch<?> patch) {
        REPORT.observe(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                false, "PENDING", "ELIGIBILITY_CHECK_EXCEPTION",
                patch == null ? "none" : patch.getClass().getName());
    }

    public static void observeExecution(LivingEntity entity, String event) {
        REPORT.observeExecution(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), event);
    }

    public static void replaceClientRendererScan(Map<ResourceLocation, String> rendererResults) {
        Map<String, String> results = new TreeMap<>();
        rendererResults.forEach((id, value) -> results.put(id.toString(), value));
        updateVersions();
        REPORT.setClientHudVisibility(NeoForgePostureClientConfig.mobHealthBarVisibility().name());
        REPORT.replaceRendererScan("client", results);
        flush(true);
    }

    public static void replaceTrustedRendererScan(Map<String, String> rendererResults) {
        REPORT.replaceRendererScan("trusted_op", rendererResults);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        REPORT.reset();
        REPORT.registerEntityIds(BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .map(ResourceLocation::toString).toList());
        updateVersions();
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            replaceTrustedRendererScan(RendererDiscoveryReportProcessor.diagnosticSnapshot(Set.of()));
        }
    }

    private static void updateVersions() {
        Map<String, String> versions = new TreeMap<>();
        ModList.get().getMods().forEach(info ->
                versions.put(info.getModId(), info.getVersion().toString()));
        REPORT.setModVersions(versions);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        flush(false);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        flush(true);
        REPORT.reset();
    }

    private static void flush(boolean force) {
        try {
            if (force) REPORT.flush(FMLPaths.CONFIGDIR.get());
            else REPORT.flushIfDue(FMLPaths.CONFIGDIR.get());
        } catch (IOException exception) {
            LOGGER.warn("Unable to write EFPI posture coverage report", exception);
        }
    }

    private NeoForgePostureCoverage() {}
}
