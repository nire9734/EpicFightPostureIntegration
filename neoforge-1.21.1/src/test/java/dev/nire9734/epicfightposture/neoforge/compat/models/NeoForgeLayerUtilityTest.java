package dev.nire9734.epicfightposture.neoforge.compat.models;

import dev.nire9734.epicfightposture.compat.humanoid.client.RuntimeMeshOwnership;
import dev.nire9734.epicfightposture.compat.humanoid.client.BoundedLruCache;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for later live Lionfish layer replacement wiring. */
class NeoForgeLayerUtilityTest {
    @Test void layerReplayDepthIsNestedAndExceptionSafe() {
        assertFalse(NeoForgeSpecialRenderHooks.isLayerReplayActive());
        assertThrows(IllegalStateException.class, () -> {
            try (var outer = NeoForgeSpecialRenderHooks.beginLayerReplay()) {
                assertTrue(NeoForgeSpecialRenderHooks.isLayerReplayActive());
                try (var inner = NeoForgeSpecialRenderHooks.beginLayerReplay()) {
                    assertTrue(NeoForgeSpecialRenderHooks.isLayerReplayActive());
                }
                assertTrue(NeoForgeSpecialRenderHooks.isLayerReplayActive());
                throw new IllegalStateException("test");
            }
        });
        assertFalse(NeoForgeSpecialRenderHooks.isLayerReplayActive());
    }

    @Test void lionfishPackedArgbRespectsAlphaAndChannelOrder() {
        var color = NeoForgeLayerColor.fromArgb(0x80402010);
        assertEquals(64.0F / 255.0F, color.red());
        assertEquals(32.0F / 255.0F, color.green());
        assertEquals(16.0F / 255.0F, color.blue());
        assertEquals(128.0F / 255.0F, color.alpha());
    }

    @Test void meshOwnershipScopeIsNestedAndExceptionSafe() {
        assertFalse(RuntimeMeshOwnership.isOwnedConstruction());
        AtomicInteger innerCalls = new AtomicInteger();
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
            RuntimeMeshOwnership.callOwned(() -> {
                assertTrue(RuntimeMeshOwnership.isOwnedConstruction());
                RuntimeMeshOwnership.callOwned(() -> {
                    assertTrue(RuntimeMeshOwnership.isOwnedConstruction());
                    innerCalls.incrementAndGet();
                    return null;
                });
                assertTrue(RuntimeMeshOwnership.isOwnedConstruction());
                throw new IllegalArgumentException("test");
            }));
        assertEquals("test", thrown.getMessage());
        assertEquals(1, innerCalls.get());
        assertFalse(RuntimeMeshOwnership.isOwnedConstruction());
    }

    @Test void boundedMeshVariantCacheEvictsLeastRecentlyUsed() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<>(2);
        cache.put("first", 1);
        cache.put("second", 2);
        assertEquals(1, cache.get("first"));
        cache.put("third", 3);
        assertTrue(cache.containsKey("first"));
        assertTrue(cache.containsKey("third"));
        assertFalse(cache.containsKey("second"));
        assertEquals(2, cache.size());
    }
}
