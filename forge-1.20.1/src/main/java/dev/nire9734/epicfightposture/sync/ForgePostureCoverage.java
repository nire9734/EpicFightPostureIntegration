package dev.nire9734.epicfightposture.sync;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.PostureCoverageReport;
import dev.nire9734.epicfightposture.PostureExecutionEligibility;
import dev.nire9734.epicfightposture.PostureClientConfig;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryReportProcessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Server-owned diagnostic report; no I/O takes place inside eligibility checks. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgePostureCoverage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PostureCoverageReport REPORT = new PostureCoverageReport("forge");

    public static void observe(LivingEntity entity, LivingEntityPatch<?> patch,
                               PostureExecutionEligibility.Result result) {
        REPORT.observe(entity.getType().builtInRegistryHolder().key().location().toString(),
                result.status().name(), result.reason().name(),
                patch == null ? "none" : patch.getClass().getName());
    }

    public static void observeGuardBreak(LivingEntity entity, boolean confirmed, String reason) {
        REPORT.observeGuardBreak(entity.getType().builtInRegistryHolder().key().location().toString(), confirmed, reason);
    }

    public static void observeEligibilityFailure(LivingEntity entity, LivingEntityPatch<?> patch) {
        REPORT.observe(entity.getType().builtInRegistryHolder().key().location().toString(),
                "PENDING", "ELIGIBILITY_CHECK_EXCEPTION",
                patch == null ? "none" : patch.getClass().getName());
    }

    public static void observeExecution(LivingEntity entity, String event) {
        REPORT.observeExecution(entity.getType().builtInRegistryHolder().key().location().toString(), event);
    }

    /** Client renderer scans also populate the same report in singleplayer. */
    public static void replaceClientRendererScan(Map<ResourceLocation, String> rendererResults) {
        Map<String, String> results = new TreeMap<>();
        rendererResults.forEach((id, value) -> results.put(id.toString(), value));
        updateVersions();
        REPORT.setClientHudVisibility(PostureClientConfig.mobHealthBarVisibility().name());
        REPORT.replaceRendererScan("client", results);
        flush(true);
    }

    /** Only called after the server has authenticated and accepted an OP report. */
    public static void replaceTrustedRendererScan(Map<String, String> rendererResults) {
        REPORT.replaceRendererScan("trusted_op", rendererResults);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        REPORT.reset();
        REPORT.registerEntityIds(ForgeRegistries.ENTITY_TYPES.getKeys().stream()
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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) flush(false);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
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

    private ForgePostureCoverage() {}
}
