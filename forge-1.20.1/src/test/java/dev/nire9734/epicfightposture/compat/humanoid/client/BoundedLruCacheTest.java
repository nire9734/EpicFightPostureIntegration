package dev.nire9734.epicfightposture.compat.humanoid.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedLruCacheTest {
    @Test
    void evictsLeastRecentlyUsedVariant() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        assertEquals(1, cache.get("a"));

        cache.put("c", 3);
        assertTrue(cache.containsKey("a"));
        assertFalse(cache.containsKey("b"));
        assertTrue(cache.containsKey("c"));
        assertEquals(2, cache.size());
    }

    @Test
    void updatedKeyDoesNotConsumeAnExtraSlot() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("a", 10);
        cache.put("c", 3);

        assertEquals(2, cache.size());
        assertEquals(10, cache.get("a"));
        assertFalse(cache.containsKey("b"));
    }

    @Test
    void eachModelCacheHasIndependentBound() {
        BoundedLruCache<Integer, Integer> first = new BoundedLruCache<>(3);
        BoundedLruCache<Integer, Integer> second = new BoundedLruCache<>(3);
        for (int i = 0; i < 100; i++) {
            first.put(i, i);
        }
        second.put(0, 7);

        assertEquals(3, first.size());
        assertEquals(1, second.size());
        assertFalse(first.containsKey(0));
        assertTrue(first.containsKey(99));
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedLruCache<>(0));
        assertThrows(IllegalArgumentException.class, () -> new BoundedLruCache<>(-1));
    }
}
