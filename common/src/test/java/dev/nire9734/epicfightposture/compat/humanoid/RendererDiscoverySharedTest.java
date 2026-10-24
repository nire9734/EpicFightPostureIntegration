package dev.nire9734.epicfightposture.compat.humanoid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RendererDiscoverySharedTest {
    @Test
    void cacheNameAndLiveActivationNoticeAreIdenticalAcrossLoaders() {
        assertEquals("renderer-discovery.json", RendererDiscoveryShared.CACHE_FILE_NAME);
        assertEquals(
                "EFPI: renderer discovery: 9 confirmed (3 vanilla humanoid, "
                        + "4 extended humanoid, 2 vanilla SpiderModel), 1 conflict(s). "
                        + "8 ready for newly spawned mobs, 1 skipped. "
                        + "No server restart is needed for new spawns; existing mobs may need to respawn.",
                RendererDiscoveryShared.runtimeAppliedMessage(
                        3, 4, 2, 1, new RendererDiscoveryRuntime.Result(8, 1))
        );
    }
}
