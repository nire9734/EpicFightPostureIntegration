package dev.nire9734.epicfightposture.sync;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.PostureCoverageReport;
import dev.nire9734.epicfightposture.PostureExecutionEligibility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.io.IOException;

/** Server-owned diagnostic report; no I/O takes place inside eligibility checks. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgePostureCoverage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PostureCoverageReport REPORT = new PostureCoverageReport("forge");

    public static void observe(LivingEntity entity, LivingEntityPatch<?> patch,
                               PostureExecutionEligibility.Comparison comparison) {
        REPORT.observe(entity.getType().builtInRegistryHolder().key().location().toString(),
                comparison.legacyEligible(), comparison.current().status().name(),
                comparison.current().reason().name(),
                patch == null ? "none" : patch.getClass().getName());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        REPORT.reset();
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
