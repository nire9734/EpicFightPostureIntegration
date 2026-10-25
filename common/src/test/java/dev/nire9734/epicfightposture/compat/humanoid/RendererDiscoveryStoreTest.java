package dev.nire9734.epicfightposture.compat.humanoid;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RendererDiscoveryStoreTest {
    @TempDir Path configDirectory;

    @Test
    void acceptsForRuntimeImmediatelyAndPersistsForNextBootstrap() {
        String server = "server-mod-set";
        ResourceLocation golem = ResourceLocation.tryParse("cataclysm:coral_golem");
        assertTrue(RendererDiscoveryStore.bootstrap(server, configDirectory).vanillaHumanoid().isEmpty());

        RendererDiscoveryStore.UpdateResult result = RendererDiscoveryStore.acceptReport(
                "a".repeat(64), Set.of(golem), Set.of(golem), Set.of(), Set.of());
        assertTrue(result.accepted());
        assertEquals(1, result.pendingCount());
        assertTrue(RendererDiscoveryStore.active().isEmpty());
        assertTrue(RendererDiscoveryStore.pending().contains(golem));
        assertTrue(RendererDiscoveryStore.pendingTargets().vanillaHumanoid().contains(golem));
        assertTrue(RendererDiscoveryStore.isRuntimeApproved(golem));
        assertTrue(RendererDiscoveryStore.isRuntimeApproved(golem,
                RendererDiscoveryRuntime.Kind.VANILLA_HUMANOID));
        assertFalse(RendererDiscoveryStore.isRuntimeApproved(golem,
                RendererDiscoveryRuntime.Kind.VANILLA_SPIDER));

        Path cache = configDirectory.resolve("epicfight_posture_integration/cache/renderer-discovery.json");
        assertTrue(Files.isRegularFile(cache));
        assertTrue(RendererDiscoveryStore.bootstrap(server, configDirectory).vanillaHumanoid().contains(golem));
        assertTrue(RendererDiscoveryStore.active().contains(golem));

        assertTrue(RendererDiscoveryStore.bootstrap("changed-server-mod-set", configDirectory)
                .vanillaHumanoid().isEmpty());
        assertTrue(RendererDiscoveryStore.pending().isEmpty());
        assertFalse(RendererDiscoveryStore.isRuntimeApproved(golem));
    }

    @Test
    void conflictingObservationsAreNotActivated() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        ResourceLocation id = ResourceLocation.tryParse("example:armored_mob");
        RendererDiscoveryStore.acceptReport("a".repeat(64), Set.of(id), Set.of(id), Set.of(), Set.of());
        RendererDiscoveryStore.acceptReport("b".repeat(64), Set.of(id), Set.of(), Set.of(), Set.of());
        assertFalse(RendererDiscoveryStore.pending().contains(id));
        assertFalse(RendererDiscoveryStore.isRuntimeApproved(id,
                RendererDiscoveryRuntime.Kind.VANILLA_HUMANOID));
        assertTrue(RendererDiscoveryStore.conflicts().contains(id));
        assertTrue(RendererDiscoveryStore.bootstrap("server", configDirectory).vanillaHumanoid().isEmpty());
    }

    @Test
    void changingRendererKindImmediatelyRevokesOldApproval() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        ResourceLocation id = ResourceLocation.tryParse("example:renderer_target");
        RendererDiscoveryStore.acceptReport("a".repeat(64), Set.of(id), Set.of(id), Set.of(), Set.of());
        assertTrue(RendererDiscoveryStore.isRuntimeApproved(id,
                RendererDiscoveryRuntime.Kind.VANILLA_HUMANOID));

        // A later report from the same environment can replace an earlier classification.
        RendererDiscoveryStore.acceptReport("a".repeat(64), Set.of(id), Set.of(), Set.of(id), Set.of());
        assertFalse(RendererDiscoveryStore.isRuntimeApproved(id,
                RendererDiscoveryRuntime.Kind.VANILLA_HUMANOID));
        assertTrue(RendererDiscoveryStore.isRuntimeApproved(id,
                RendererDiscoveryRuntime.Kind.EXTENDED_HUMANOID));
    }

    @Test
    void operatorsWithIdenticalModSetsKeepIndependentRendererReports() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String environment = "a".repeat(64);
        ResourceLocation golem = ResourceLocation.tryParse("cataclysm:coral_golem");

        assertTrue(RendererDiscoveryStore.acceptOperatorReport(first, environment,
                Set.of(golem), Set.of(golem), Set.of(), Set.of()).accepted());
        assertTrue(RendererDiscoveryStore.isRuntimeApproved(golem));

        // Same mod versions, but another operator's client lacks this renderer.
        assertTrue(RendererDiscoveryStore.acceptOperatorReport(second, environment,
                Set.of(golem), Set.of(), Set.of(), Set.of()).accepted());
        assertFalse(RendererDiscoveryStore.isRuntimeApproved(golem));
        assertTrue(RendererDiscoveryStore.conflicts().contains(golem));
        assertFalse(RendererDiscoveryStore.bootstrap("server", configDirectory)
                .vanillaHumanoid().contains(golem));

        // Re-reporting from the first OP must not erase the second OP's findings.
        assertTrue(RendererDiscoveryStore.acceptOperatorReport(first, environment,
                Set.of(golem), Set.of(golem), Set.of(), Set.of()).accepted());
        assertFalse(RendererDiscoveryStore.isRuntimeApproved(golem));

        // Once the second OP agrees, the target becomes usable again.
        assertTrue(RendererDiscoveryStore.acceptOperatorReport(second, environment,
                Set.of(golem), Set.of(golem), Set.of(), Set.of()).accepted());
        assertTrue(RendererDiscoveryStore.isRuntimeApproved(golem));
        assertFalse(RendererDiscoveryStore.conflicts().contains(golem));
    }

    @Test
    void operatorChangingEnvironmentReplacesStaleReport() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        UUID operator = UUID.randomUUID();
        ResourceLocation id = ResourceLocation.tryParse("example:mob");
        RendererDiscoveryStore.acceptOperatorReport(operator, "a".repeat(64),
                Set.of(id), Set.of(id), Set.of(), Set.of());
        assertTrue(RendererDiscoveryStore.isRuntimeApproved(id));

        RendererDiscoveryStore.acceptOperatorReport(operator, "b".repeat(64),
                Set.of(id), Set.of(), Set.of(id), Set.of());
        assertTrue(RendererDiscoveryStore.isRuntimeApproved(id,
                RendererDiscoveryRuntime.Kind.EXTENDED_HUMANOID));
        assertFalse(RendererDiscoveryStore.conflicts().contains(id));
    }

    @Test
    void identicalOperatorReportDoesNotRewriteTheCache() throws Exception {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        UUID operator = UUID.randomUUID();
        ResourceLocation id = ResourceLocation.tryParse("example:stable");
        String fingerprint = "a".repeat(64);
        RendererDiscoveryStore.acceptOperatorReport(operator, fingerprint,
                Set.of(id), Set.of(id), Set.of(), Set.of());

        Path cache = configDirectory.resolve("epicfight_posture_integration/cache/renderer-discovery.json");
        FileTime marker = FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(cache, marker);
        assertTrue(RendererDiscoveryStore.acceptOperatorReport(operator, fingerprint,
                Set.of(id), Set.of(id), Set.of(), Set.of()).accepted());
        assertEquals(marker, Files.getLastModifiedTime(cache));
        assertTrue(RendererDiscoveryStore.isRuntimeApproved(id));
    }
}
