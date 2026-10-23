package dev.nire9734.epicfightposture.compat.humanoid;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModSetFingerprintTest {
    @Test void independentOfEnumerationOrder() {
        assertEquals(ModSetFingerprint.fromEntries(List.of("b@2", "a@1")),
                ModSetFingerprint.fromEntries(List.of("a@1", "b@2")));
        assertNotEquals(ModSetFingerprint.fromEntries(List.of("a@1")),
                ModSetFingerprint.fromEntries(List.of("a@2")));
        assertTrue(ModSetFingerprint.fromEntries(List.of("a@1")).matches("[0-9a-f]{64}"));
    }
}
