package dev.nire9734.epicfightposture.compat.humanoid;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RendererDiscoveryStoreTest {
    @TempDir Path configDirectory;

    @Test
    void acceptsAndPromotesOnlyAfterRestart() {
        String server = "server-mod-set";
        ResourceLocation golem = ResourceLocation.tryParse("cataclysm:coral_golem");
        assertTrue(RendererDiscoveryStore.bootstrap(server, configDirectory).vanillaHumanoid().isEmpty());

        RendererDiscoveryStore.UpdateResult result = RendererDiscoveryStore.acceptReport(
                "a".repeat(64), Set.of(golem), Set.of(golem), Set.of(), Set.of());
        assertTrue(result.accepted());
        assertTrue(result.restartRequired());
        assertTrue(RendererDiscoveryStore.active().isEmpty());
        assertTrue(RendererDiscoveryStore.pending().contains(golem));

        Path cache = configDirectory.resolve("epicfight_posture_integration/cache/renderer-discovery.json");
        assertTrue(Files.isRegularFile(cache));
        assertTrue(RendererDiscoveryStore.bootstrap(server, configDirectory).vanillaHumanoid().contains(golem));
        assertTrue(RendererDiscoveryStore.active().contains(golem));

        assertTrue(RendererDiscoveryStore.bootstrap("changed-server-mod-set", configDirectory)
                .vanillaHumanoid().isEmpty());
        assertTrue(RendererDiscoveryStore.pending().isEmpty());
    }

    @Test
    void conflictingObservationsAreNotActivated() {
        RendererDiscoveryStore.bootstrap("server", configDirectory);
        ResourceLocation id = ResourceLocation.tryParse("example:armored_mob");
        RendererDiscoveryStore.acceptReport("a".repeat(64), Set.of(id), Set.of(id), Set.of(), Set.of());
        RendererDiscoveryStore.acceptReport("b".repeat(64), Set.of(id), Set.of(), Set.of(), Set.of());
        assertFalse(RendererDiscoveryStore.pending().contains(id));
        assertTrue(RendererDiscoveryStore.conflicts().contains(id));
        assertTrue(RendererDiscoveryStore.bootstrap("server", configDirectory).vanillaHumanoid().isEmpty());
    }
}
