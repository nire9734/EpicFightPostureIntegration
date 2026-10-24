package dev.nire9734.epicfightposture.compat.humanoid;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
