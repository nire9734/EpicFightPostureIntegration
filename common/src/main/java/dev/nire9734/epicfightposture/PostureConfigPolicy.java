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

    /** Log text shared by both loaders when the parry range is configured backwards. */
    public static final String INVERTED_PARRY_RANGE_WARNING =
            "EFPI posture.parry_base_min_percent ({}) is greater than posture.parry_base_max_percent ({}); "
                    + "the two values are used in swapped order";

    /**
     * True when an operator configured the parry range backwards. The balance
     * math already uses the values in the intended order; this only drives a warning.
     */
    public static boolean isParryRangeInverted(double maxBasePercent, double minBasePercent) {
        return Double.isFinite(maxBasePercent) && Double.isFinite(minBasePercent)
                && minBasePercent > maxBasePercent;
    }

    private PostureConfigPolicy() {}
}
