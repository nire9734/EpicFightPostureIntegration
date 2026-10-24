package dev.nire9734.epicfightposture.compat.humanoid;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererDiscoveryRuntimeTest {
    @Test
    void dispatchesEveryTrustedKindAndCountsRejectedRegistrations() {
        ResourceLocation humanoid = ResourceLocation.tryParse("example:humanoid");
        ResourceLocation extended = ResourceLocation.tryParse("example:extended");
        ResourceLocation spider = ResourceLocation.tryParse("example:spider");
        Set<String> observed = new HashSet<>();

        RendererDiscoveryRuntime.Result result = RendererDiscoveryRuntime.activate(
                new RendererDiscoveryStore.ActiveTargets(
                        Set.of(humanoid), Set.of(extended), Set.of(spider)),
                (id, kind) -> {
                    observed.add(id + ":" + kind);
                    return kind != RendererDiscoveryRuntime.Kind.EXTENDED_HUMANOID;
                });

        assertEquals(2, result.ready());
        assertEquals(1, result.skipped());
        assertEquals(3, observed.size());
        assertTrue(observed.contains(humanoid + ":VANILLA_HUMANOID"));
        assertTrue(observed.contains(extended + ":EXTENDED_HUMANOID"));
        assertTrue(observed.contains(spider + ":VANILLA_SPIDER"));
    }

    @Test
    void repeatedReportDoesNotCountAsNewActivation() {
        ResourceLocation golem = ResourceLocation.tryParse("cataclysm:coral_golem");
        RendererDiscoveryStore.ActiveTargets targets = new RendererDiscoveryStore.ActiveTargets(
                Set.of(golem), Set.of(), Set.of());
        RendererDiscoveryRuntime.ActivationUpdate first =
                RendererDiscoveryRuntime.activateTrackingChanges(
                        RendererDiscoveryStore.ActiveTargets.empty(), targets, (id, kind) -> true);
        assertEquals(1, first.newlyReady());

        RendererDiscoveryRuntime.ActivationUpdate again =
                RendererDiscoveryRuntime.activateTrackingChanges(targets, targets, (id, kind) -> true);
        assertEquals(1, again.ready());
        assertEquals(0, again.newlyReady());
        assertEquals(0, again.newlySkipped());
        assertEquals(0, RendererDiscoveryRuntime.withdrawnTargets(targets, targets));
        assertEquals(1, RendererDiscoveryRuntime.withdrawnTargets(
                targets, RendererDiscoveryStore.ActiveTargets.empty()));
    }
}
