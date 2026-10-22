package dev.nire9734.epicfightposture.neoforge.compat.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Access-ordered cache for runtime mesh variants. Callers own synchronization.
 * Each source baked model uses its own instance, while the outer map is weak-keyed.
 */
final class BoundedLruCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxEntries;

    BoundedLruCache(int maxEntries) {
        super(16, 0.75F, true);
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxEntries;
    }
}
