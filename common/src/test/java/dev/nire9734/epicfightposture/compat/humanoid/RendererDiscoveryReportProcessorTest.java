package dev.nire9734.epicfightposture.compat.humanoid;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RendererDiscoveryReportProcessorTest {
    @TempDir Path configDirectory;

    @Test
    void processingAndNoticesAreIdenticalForBothLoaderAdapters() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        UUID operator = UUID.randomUUID();
        ResourceLocation golem = ResourceLocation.tryParse("cataclysm:coral_golem");
        ResourceLocation nonexistent = ResourceLocation.tryParse("example:missing");
        AtomicInteger refreshes = new AtomicInteger();

        var first = RendererDiscoveryReportProcessor.process(
                operator, "a".repeat(64), Set.of(golem, nonexistent), Set.of(golem, nonexistent),
                Set.of(), Set.of(), golem::equals, (id, kind) -> true,
                () -> { refreshes.incrementAndGet(); return new RendererDiscoveryRuntime.LiveRefresh(3, 0); });
        assertTrue(first.accepted());
        assertEquals(1, first.report().pendingCount());
        assertEquals(1, first.activation().newlyReady());
        assertTrue(first.operatorNotice().contains("3 existing mobs patched"));
        assertTrue(first.acceptanceSummary("Operator").contains("3 existing mobs updated, 0 failed"));

        var repeat = RendererDiscoveryReportProcessor.process(
                operator, "a".repeat(64), Set.of(golem, nonexistent), Set.of(golem, nonexistent),
                Set.of(), Set.of(), golem::equals, (id, kind) -> true,
                () -> { refreshes.incrementAndGet(); return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });
        assertTrue(repeat.accepted());
        assertNull(repeat.operatorNotice());
        assertEquals(2, refreshes.get());

        var conflicting = RendererDiscoveryReportProcessor.process(
                UUID.randomUUID(), "a".repeat(64), Set.of(golem), Set.of(), Set.of(), Set.of(),
                id -> true, (id, kind) -> true,
                () -> new RendererDiscoveryRuntime.LiveRefresh(0, 0));
        assertTrue(conflicting.accepted());
        assertEquals(1, conflicting.report().conflictCount());
        assertTrue(conflicting.operatorNotice().contains("renderer conflicts"));
        assertFalse(RendererDiscoveryStore.isRuntimeApproved(golem));
    }

    @Test
    void rejectedReportsDoNotRegisterOrRefreshAnything() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        AtomicInteger calls = new AtomicInteger();
        var outcome = RendererDiscoveryReportProcessor.process(
                UUID.randomUUID(), "invalid", Set.of(), Set.of(), Set.of(), Set.of(),
                id -> { calls.incrementAndGet(); return true; },
                (id, kind) -> { calls.incrementAndGet(); return true; },
                () -> { calls.incrementAndGet(); return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });
        assertFalse(outcome.accepted());
        assertEquals(RendererDiscoveryShared.ValidationError.INVALID_FINGERPRINT, outcome.validationError());
        assertEquals(0, calls.get());
    }
}
