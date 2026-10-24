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
                        + "12 already-loaded mob(s) updated. No server restart required.",
                RendererDiscoveryShared.runtimeAppliedMessage(
                        3, 4, 2, 1, new RendererDiscoveryRuntime.Result(8, 1),
                        new RendererDiscoveryRuntime.LiveRefresh(12, 0))
        );
        assertEquals(
                "EFPI: renderer discovery: 9 confirmed (3 vanilla humanoid, "
                        + "4 extended humanoid, 2 vanilla SpiderModel), 1 conflict(s). "
                        + "8 ready for newly spawned mobs, 1 skipped. "
                        + "8 already-loaded mob(s) updated. 4 mob(s) could not be updated (see server log).",
                RendererDiscoveryShared.runtimeAppliedMessage(
                        3, 4, 2, 1, new RendererDiscoveryRuntime.Result(8, 1),
                        new RendererDiscoveryRuntime.LiveRefresh(8, 4))
        );
    }
}
