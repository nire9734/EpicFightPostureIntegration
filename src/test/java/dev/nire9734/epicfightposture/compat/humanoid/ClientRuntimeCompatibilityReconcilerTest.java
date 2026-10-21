package dev.nire9734.epicfightposture.compat.humanoid;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientRuntimeCompatibilityReconcilerTest {
    @Test
    void rejectsUnverifiedPreviouslyInstalledPatches() {
        Set<String> rejected = new HashSet<>();
        ClientRuntimeCompatibilityReconciler.reconcile(
                Set.of("vanilla", "spider", "extended"),
                Set.of("extended"),
                rejected);
        assertEquals(Set.of("vanilla", "spider"), rejected);
    }

    @Test
    void successfulRescanReactivatesOnlyVerifiedOwnedPatches() {
        Set<String> rejected = new HashSet<>(Set.of("vanilla", "spider", "external"));
        ClientRuntimeCompatibilityReconciler.reconcile(
                Set.of("vanilla", "spider"),
                Set.of("vanilla", "external"),
                rejected);
        assertEquals(Set.of("spider", "external"), rejected);
    }

    @Test
    void nullOrEmptyScanFailsClosed() {
        Set<String> rejected = new HashSet<>();
        ClientRuntimeCompatibilityReconciler.reconcile(Set.of("vanilla"), null, rejected);
        assertEquals(Set.of("vanilla"), rejected);
    }
}
