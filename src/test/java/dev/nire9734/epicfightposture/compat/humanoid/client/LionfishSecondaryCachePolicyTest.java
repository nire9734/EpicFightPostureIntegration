package dev.nire9734.epicfightposture.compat.humanoid.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LionfishSecondaryCachePolicyTest {
    @Test
    void failedSecondaryMeshIsNeverCached() {
        var unsupported = LionfishSecondaryModelLayerBridge.CachedSecondary.unsupported();
        assertFalse(LionfishSecondaryModelLayerBridge.shouldCache(unsupported));
        assertFalse(LionfishSecondaryModelLayerBridge.shouldReplace(unsupported));
    }

    @Test
    void nullResultIsNeverCached() {
        assertFalse(LionfishSecondaryModelLayerBridge.shouldCache(null));
    }

    @Test
    void cacheEvictsOlderSuccessfulVariantsAtCapacity() {
        BoundedLruCache<Integer, String> variants = new BoundedLruCache<>(64);
        for (int i = 0; i < 128; i++) {
            variants.put(i, "mesh-" + i);
        }
        assertTrue(variants.size() == 64);
        assertFalse(variants.containsKey(0));
        assertTrue(variants.containsKey(127));
    }
}
