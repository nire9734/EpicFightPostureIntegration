package dev.nire9734.epicfightposture;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity types whose special-state renderer failed in this session. A failed
 * type falls back to its normal renderer until renderers are rebuilt (for
 * example after a resource reload), so one broken model cannot crash the game
 * or fail every frame.
 *
 * Plain Java: render gates on both loaders consult it, and those gates are
 * also loaded on dedicated servers.
 */
public final class SpecialRenderFailures {
    private static final Set<Object> DISABLED = ConcurrentHashMap.newKeySet();

    public static boolean isDisabled(Object entityType) {
        return entityType != null && DISABLED.contains(entityType);
    }

    /** Disables the type; true only for its first failure, so callers log once. */
    public static boolean disable(Object entityType) {
        return entityType != null && DISABLED.add(entityType);
    }

    /** Renderers were rebuilt: give every type another chance. */
    public static void clear() {
        DISABLED.clear();
    }

    private SpecialRenderFailures() {
    }
}
