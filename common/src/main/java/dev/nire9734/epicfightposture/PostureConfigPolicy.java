package dev.nire9734.epicfightposture;

import java.util.Set;

/** Platform-independent operator policy for EFPI-owned automatic patches. */
public final class PostureConfigPolicy {
    public static boolean allowsAutomatic(boolean enabled, String namespace,
            Set<String> allowlist, Set<String> denylist) {
        if (!enabled || namespace == null) return false;
        if ("minecraft".equals(namespace)) return true;
        if (denylist.contains(namespace)) return false;
        return allowlist.isEmpty() || allowlist.contains(namespace);
    }

    public static <T> boolean blocksPosture(T id, Set<T> blacklist,
            boolean efpiOwned, boolean automaticAllowed) {
        return id != null && (blacklist.contains(id) || (efpiOwned && !automaticAllowed));
    }

    /** Client safety opt-outs and server policy are additive, never overriding one another. */
    public static <T> boolean isExcluded(T id, Set<T> localBlacklist, Set<T> serverBlacklist) {
        return id != null && (localBlacklist.contains(id) || serverBlacklist.contains(id));
    }

    private PostureConfigPolicy() {}
}
