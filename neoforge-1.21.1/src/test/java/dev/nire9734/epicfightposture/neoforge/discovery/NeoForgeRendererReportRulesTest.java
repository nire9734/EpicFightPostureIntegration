package dev.nire9734.epicfightposture.neoforge.discovery;

import java.util.Set;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeoForgeRendererReportRulesTest {
    private static final String VALID_FINGERPRINT = "a".repeat(64);

    @Test void acceptsSubsetOfInspected() {
        assertTrue(NeoForgeRendererReportRules.isConsistent(VALID_FINGERPRINT,
                Set.of("minecraft:bogged", "other:modded_knight"),
                Set.of("other:modded_knight")));
    }

    @Test void rejectsUninspectedAndInvalidFingerprints() {
        assertFalse(NeoForgeRendererReportRules.isConsistent(VALID_FINGERPRINT,
                Set.of("minecraft:bogged"), Set.of("other:knight")));
        assertFalse(NeoForgeRendererReportRules.isConsistent("invalid",
                Set.of("minecraft:bogged"), Set.of()));
        assertFalse(NeoForgeRendererReportRules.isConsistent("A".repeat(64),
                Set.of("minecraft:bogged"), Set.of()));
    }

    @Test void rejectsOversizedAndNullReports() {
        Set<Integer> tooLarge = new HashSet<>();
        for (int i = 0; i <= 8192; i++) tooLarge.add(i);
        assertFalse(NeoForgeRendererReportRules.isConsistent(VALID_FINGERPRINT, tooLarge, Set.of()));
        assertFalse(NeoForgeRendererReportRules.isConsistent(VALID_FINGERPRINT, null, Set.of()));
        assertFalse(NeoForgeRendererReportRules.isConsistent(VALID_FINGERPRINT, Set.of(), null));
    }
    @Test void acceptsSeparateBackendClassifications() {
        assertTrue(NeoForgeRendererReportRules.isConsistent(VALID_FINGERPRINT,
                Set.of("minecraft:bogged", "example:knight", "example:spider"),
                Set.of("minecraft:bogged"), Set.of("example:knight"), Set.of("example:spider")));
    }

    @Test void rejectsOverlappingBackendClassifications() {
        assertFalse(NeoForgeRendererReportRules.isConsistent(VALID_FINGERPRINT,
                Set.of("example:spider"), Set.of("example:spider"),
                Set.of(), Set.of("example:spider")));
        assertFalse(NeoForgeRendererReportRules.isConsistent(VALID_FINGERPRINT,
                Set.of("example:mob"), Set.of(), Set.of("example:mob"), Set.of("example:mob")));
    }

    @Test void rejectsUnknownExtendedOrSpiderEvidence() {
        assertFalse(NeoForgeRendererReportRules.isConsistent(VALID_FINGERPRINT,
                Set.of("minecraft:bogged"), Set.of(), Set.of("example:knight"), Set.of()));
        assertFalse(NeoForgeRendererReportRules.isConsistent(VALID_FINGERPRINT,
                Set.of("minecraft:bogged"), Set.of(), Set.of(), Set.of("example:spider")));
    }

}
