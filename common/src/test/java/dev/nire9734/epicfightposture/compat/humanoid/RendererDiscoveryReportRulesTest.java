package dev.nire9734.epicfightposture.compat.humanoid;

import java.util.Set;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RendererDiscoveryReportRulesTest {
    private static final String VALID_FINGERPRINT = "a".repeat(64);

    @Test void acceptsSubsetOfInspected() {
        assertValid(VALID_FINGERPRINT,
                Set.of("minecraft:bogged", "other:modded_knight"),
                Set.of("other:modded_knight"));
    }

    @Test void rejectsUninspectedAndInvalidFingerprints() {
        assertInvalid(VALID_FINGERPRINT,
                Set.of("minecraft:bogged"), Set.of("other:knight"));
        assertInvalid("invalid", Set.of("minecraft:bogged"), Set.of());
        assertInvalid("A".repeat(64), Set.of("minecraft:bogged"), Set.of());
    }

    @Test void rejectsOversizedAndNullReports() {
        Set<Integer> tooLarge = new HashSet<>();
        for (int i = 0; i <= 8192; i++) tooLarge.add(i);
        assertInvalid(VALID_FINGERPRINT, tooLarge, Set.of());
        assertInvalid(VALID_FINGERPRINT, null, Set.of());
        assertInvalid(VALID_FINGERPRINT, Set.of(), null);
    }
    @Test void acceptsSeparateBackendClassifications() {
        assertValid(VALID_FINGERPRINT,
                Set.of("minecraft:bogged", "example:knight", "example:spider"),
                Set.of("minecraft:bogged"), Set.of("example:knight"), Set.of("example:spider"));
    }

    @Test void rejectsOverlappingBackendClassifications() {
        assertInvalid(VALID_FINGERPRINT,
                Set.of("example:spider"), Set.of("example:spider"),
                Set.of(), Set.of("example:spider"));
        assertInvalid(VALID_FINGERPRINT,
                Set.of("example:mob"), Set.of(), Set.of("example:mob"), Set.of("example:mob"));
    }

    @Test void rejectsUnknownExtendedOrSpiderEvidence() {
        assertInvalid(VALID_FINGERPRINT,
                Set.of("minecraft:bogged"), Set.of(), Set.of("example:knight"), Set.of());
        assertInvalid(VALID_FINGERPRINT,
                Set.of("minecraft:bogged"), Set.of(), Set.of(), Set.of("example:spider"));
    }

    private static void assertValid(String fingerprint, Set<?> inspected, Set<?> vanilla) {
        assertValid(fingerprint, inspected, vanilla, Set.of(), Set.of());
    }

    private static void assertValid(String fingerprint, Set<?> inspected,
                                    Set<?> vanilla, Set<?> extended, Set<?> spider) {
        assertEquals(RendererDiscoveryShared.ValidationError.NONE,
                RendererDiscoveryShared.validateReport(fingerprint, inspected, vanilla, extended, spider));
    }

    private static void assertInvalid(String fingerprint, Set<?> inspected, Set<?> vanilla) {
        assertInvalid(fingerprint, inspected, vanilla, Set.of(), Set.of());
    }

    private static void assertInvalid(String fingerprint, Set<?> inspected,
                                      Set<?> vanilla, Set<?> extended, Set<?> spider) {
        assertNotEquals(RendererDiscoveryShared.ValidationError.NONE,
                RendererDiscoveryShared.validateReport(fingerprint, inspected, vanilla, extended, spider));
    }

}
