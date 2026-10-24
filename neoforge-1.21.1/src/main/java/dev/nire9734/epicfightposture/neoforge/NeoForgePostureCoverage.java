package dev.nire9734.epicfightposture.neoforge;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.PostureCoverageReport;
import dev.nire9734.epicfightposture.PostureExecutionEligibility;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.io.IOException;

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

    private static void onServerStarted(ServerStartedEvent event) {
        REPORT.reset();
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
