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
        AtomicInteger registrations = new AtomicInteger();

        var first = RendererDiscoveryReportProcessor.process(
                operator, "a".repeat(64), Set.of(golem, nonexistent), Set.of(golem, nonexistent),
                Set.of(), Set.of(), golem::equals, (id, kind) -> {
                    registrations.incrementAndGet(); return true;
                },
                affected -> {
                    assertEquals(Set.of(golem), affected);
                    refreshes.incrementAndGet(); return new RendererDiscoveryRuntime.LiveRefresh(3, 0);
                });
        assertTrue(first.accepted());
        assertEquals(1, first.report().pendingCount());
        assertEquals(1, first.activation().newlyReady());
        assertTrue(first.operatorNotice().contains("3 existing mobs patched"));
        assertTrue(first.acceptanceSummary("Operator").contains("3 existing mobs updated, 0 failed"));

        var repeat = RendererDiscoveryReportProcessor.process(
                operator, "a".repeat(64), Set.of(golem, nonexistent), Set.of(golem, nonexistent),
                Set.of(), Set.of(), golem::equals, (id, kind) -> {
                    registrations.incrementAndGet(); return true;
                },
                affected -> { refreshes.incrementAndGet(); return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });
        assertTrue(repeat.accepted());
        assertNull(repeat.operatorNotice());
        assertEquals(1, refreshes.get());
        assertEquals(1, registrations.get());
        assertEquals(1, repeat.activation().ready());

        var conflicting = RendererDiscoveryReportProcessor.process(
                UUID.randomUUID(), "a".repeat(64), Set.of(golem), Set.of(), Set.of(), Set.of(),
                id -> true, (id, kind) -> true,
                affected -> new RendererDiscoveryRuntime.LiveRefresh(0, 0));
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
                affected -> { calls.incrementAndGet(); return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });
        assertFalse(outcome.accepted());
        assertEquals(RendererDiscoveryShared.ValidationError.INVALID_FINGERPRINT, outcome.validationError());
        assertEquals(0, calls.get());
    }

    @Test
    void scansOnlyNewlyApprovedTypesAndRetainsIndependentOperatorReports() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        ResourceLocation golem = ResourceLocation.tryParse("example:golem");
        ResourceLocation knight = ResourceLocation.tryParse("example:knight");
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();

        var first = RendererDiscoveryReportProcessor.process(
                UUID.randomUUID(), "a".repeat(64), Set.of(golem), Set.of(golem), Set.of(), Set.of(),
                id -> true, (id, kind) -> { registrations.incrementAndGet(); return true; },
                affected -> { assertEquals(Set.of(golem), affected); refreshes.incrementAndGet();
                    return new RendererDiscoveryRuntime.LiveRefresh(1, 0); });
        assertTrue(first.accepted());

        var second = RendererDiscoveryReportProcessor.process(
                UUID.randomUUID(), "a".repeat(64), Set.of(golem), Set.of(golem), Set.of(), Set.of(),
                id -> true, (id, kind) -> { registrations.incrementAndGet(); return true; },
                affected -> { refreshes.incrementAndGet(); return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });
        assertTrue(second.accepted());
        assertNull(second.operatorNotice());
        assertEquals(1, registrations.get());
        assertEquals(1, refreshes.get());

        var third = RendererDiscoveryReportProcessor.process(
                UUID.randomUUID(), "a".repeat(64), Set.of(golem, knight),
                Set.of(golem, knight), Set.of(), Set.of(), id -> true,
                (id, kind) -> { registrations.incrementAndGet(); return true; },
                affected -> { assertEquals(Set.of(knight), affected); refreshes.incrementAndGet();
                    return new RendererDiscoveryRuntime.LiveRefresh(2, 0); });
        assertTrue(third.accepted());
        assertEquals(2, third.activation().ready());
        assertEquals(1, third.activation().newlyReady());
        assertEquals(2, registrations.get());
        assertEquals(2, refreshes.get());
    }

    @Test
    void failedRegistrationsAndLiveRefreshesAreRetriedOnUnchangedReports() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        ResourceLocation id = ResourceLocation.tryParse("example:recoverable");
        UUID operator = UUID.randomUUID();
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();

        for (int attempt = 1; attempt <= 3; attempt++) {
            var outcome = RendererDiscoveryReportProcessor.process(
                    operator, "a".repeat(64), Set.of(id), Set.of(id), Set.of(), Set.of(),
                    ignored -> true, (target, kind) -> registrations.incrementAndGet() > 1,
                    affected -> {
                        assertEquals(Set.of(id), affected);
                        return new RendererDiscoveryRuntime.LiveRefresh(0, refreshes.incrementAndGet() == 1 ? 1 : 0);
                    });
            assertTrue(outcome.accepted());
        }

        assertEquals(2, registrations.get());
        assertEquals(2, refreshes.get());
        // After both steps succeeded, the unchanged fourth report is a no-op.
        var unchanged = RendererDiscoveryReportProcessor.process(
                operator, "a".repeat(64), Set.of(id), Set.of(id), Set.of(), Set.of(),
                ignored -> true, (target, kind) -> { fail("Unexpected registration"); return false; },
                affected -> { fail("Unexpected refresh"); return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });
        assertNull(unchanged.operatorNotice());
    }

    @Test
    void serverBootstrapInvalidatesSuccessEvenWhenApprovedTargetsStayTheSame() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        ResourceLocation id = ResourceLocation.tryParse("example:retained");
        UUID operator = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        RendererDiscoveryReportProcessor.process(operator, "a".repeat(64), Set.of(id), Set.of(id),
                Set.of(), Set.of(), ignored -> true,
                (target, kind) -> { calls.incrementAndGet(); return true; },
                affected -> new RendererDiscoveryRuntime.LiveRefresh(0, 0));
        assertEquals(1, calls.get());

        RendererDiscoveryStore.bootstrap("server", configDirectory);
        RendererDiscoveryReportProcessor.process(operator, "a".repeat(64), Set.of(id), Set.of(id),
                Set.of(), Set.of(), ignored -> true,
                (target, kind) -> { calls.incrementAndGet(); return true; },
                affected -> { assertEquals(Set.of(id), affected); return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });
        assertEquals(2, calls.get());
    }

    @Test
    void registryRebuildInvalidatesRegistrationAndForcesLiveRefresh() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        ResourceLocation id = ResourceLocation.tryParse("example:rebuild");
        UUID operator = UUID.randomUUID();
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        for (int index = 0; index < 2; index++) {
            RendererDiscoveryReportProcessor.process(operator, "a".repeat(64), Set.of(id),
                    Set.of(id), Set.of(), Set.of(), ignored -> true,
                    (target, kind) -> { registrations.incrementAndGet(); return true; },
                    affected -> { assertEquals(Set.of(id), affected); refreshes.incrementAndGet();
                        return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });
        }
        assertEquals(1, registrations.get());
        assertEquals(1, refreshes.get());

        RendererDiscoveryReportProcessor.invalidateRuntimeValidation();
        RendererDiscoveryReportProcessor.process(operator, "a".repeat(64), Set.of(id),
                Set.of(id), Set.of(), Set.of(), ignored -> true,
                (target, kind) -> { registrations.incrementAndGet(); return true; },
                affected -> { assertEquals(Set.of(id), affected); refreshes.incrementAndGet();
                    return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });
        assertEquals(2, registrations.get());
        assertEquals(2, refreshes.get());
    }

    @Test
    void conflictsAreStillReportedWhenNoMobTypeWasEverApproved() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        ResourceLocation id = ResourceLocation.tryParse("example:unsupported");
        RendererDiscoveryReportProcessor.process(UUID.randomUUID(), "a".repeat(64),
                Set.of(id), Set.of(), Set.of(), Set.of(), ignored -> true,
                (target, kind) -> { fail("No target was approved"); return false; },
                affected -> { fail("No live refresh needed"); return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });

        var conflict = RendererDiscoveryReportProcessor.process(UUID.randomUUID(), "a".repeat(64),
                Set.of(id), Set.of(id), Set.of(), Set.of(), ignored -> true,
                (target, kind) -> { fail("Conflicted targets must not register"); return false; },
                affected -> { fail("Conflicted targets must not refresh"); return new RendererDiscoveryRuntime.LiveRefresh(0, 0); });
        assertTrue(conflict.accepted());
        assertFalse(RendererDiscoveryStore.isRuntimeApproved(id));
        assertTrue(conflict.operatorNotice().contains("renderer conflicts"));
    }

    @Test
    void unexpectedAdapterFailuresAreReportedAndRemainRetryable() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        ResourceLocation id = ResourceLocation.tryParse("example:unstable");
        UUID operator = UUID.randomUUID();
        var failed = RendererDiscoveryReportProcessor.process(operator, "a".repeat(64),
                Set.of(id), Set.of(id), Set.of(), Set.of(), ignored -> true,
                (target, kind) -> { throw new IllegalStateException("registration exception"); },
                affected -> { throw new IllegalStateException("refresh exception"); });
        assertTrue(failed.accepted());
        assertEquals(1, failed.activation().skipped());
        assertEquals(1, failed.refresh().failed());
        assertTrue(failed.operatorNotice().contains("patch failures"));

        AtomicInteger callbacks = new AtomicInteger();
        var recovered = RendererDiscoveryReportProcessor.process(operator, "a".repeat(64),
                Set.of(id), Set.of(id), Set.of(), Set.of(), ignored -> true,
                (target, kind) -> { callbacks.incrementAndGet(); return true; },
                affected -> { callbacks.incrementAndGet(); return new RendererDiscoveryRuntime.LiveRefresh(1, 0); });
        assertTrue(recovered.accepted());
        assertEquals(2, callbacks.get());
        assertEquals(1, recovered.activation().ready());
    }
}
