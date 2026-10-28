package dev.nire9734.epicfightposture.compat.humanoid;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererDiscoverySharedTest {
    @Test
    void cacheNameIsIdenticalAcrossLoaders() {
        assertEquals("renderer-discovery.json", RendererDiscoveryShared.CACHE_FILE_NAME);
    }

    @Test
    void invalidFingerprintIsRejected() {
        assertEquals(RendererDiscoveryShared.ValidationError.INVALID_FINGERPRINT,
                RendererDiscoveryShared.validateReport("invalid", Set.of(), Set.of(), Set.of(), Set.of()));
    }

    @Test
    void onlyActualChangesOrProblemsTriggerOperatorMessages() {
        assertNull(RendererDiscoveryShared.changeNotice(0, 0, 0, 0, 0, 0));

        String success = RendererDiscoveryShared.changeNotice(2, 0, 18, 0, 0, 0);
        assertTrue(success.contains("2 mob types enabled"));
        assertTrue(success.contains("18 existing mobs patched"));

        String failure = RendererDiscoveryShared.changeNotice(0, 1, 0, 1, 0, 2);
        assertTrue(failure.contains("1 renderer conflicts"));
        assertTrue(failure.contains("2 patch failures"));
        assertTrue(failure.contains("See server log"));
        assertTrue(RendererDiscoveryShared.reportErrorNotice().contains("failed"));
    }
}
