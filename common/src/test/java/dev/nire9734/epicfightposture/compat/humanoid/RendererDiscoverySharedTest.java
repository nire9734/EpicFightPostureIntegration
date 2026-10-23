package dev.nire9734.epicfightposture.compat.humanoid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RendererDiscoverySharedTest {
    @Test
    void cacheNameAndRestartNoticeAreIdenticalAcrossLoaders() {
        assertEquals("renderer-discovery.json", RendererDiscoveryShared.CACHE_FILE_NAME);
        assertEquals(
                "EFPI: renderer discovery: 9 confirmed (3 vanilla humanoid, "
                        + "4 extended humanoid, 2 vanilla SpiderModel), 1 conflict(s)."
                        + " Restart the server to apply the pending compatibility list.",
                RendererDiscoveryShared.restartRequiredMessage(3, 4, 2, 1)
        );
    }
}
