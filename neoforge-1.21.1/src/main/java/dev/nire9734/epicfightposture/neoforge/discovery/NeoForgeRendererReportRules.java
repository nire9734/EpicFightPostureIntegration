package dev.nire9734.epicfightposture.neoforge.discovery;
import java.util.Set;

/** Pure validation: submitted renderer classes must be disjoint subsets of inspected IDs. */
public final class NeoForgeRendererReportRules {
    private static final int MAX_IDS = 8192;

    public static boolean isConsistent(String fingerprint, Set<?> inspected, Set<?> verified) {
        return isConsistent(fingerprint, inspected, verified, Set.of(), Set.of());
    }

    public static boolean isConsistent(String fingerprint, Set<?> inspected,
                                       Set<?> vanilla, Set<?> extended, Set<?> spiders) {
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")
                || inspected == null || vanilla == null || extended == null || spiders == null
                || inspected.size() > MAX_IDS || vanilla.size() > MAX_IDS
                || extended.size() > MAX_IDS || spiders.size() > MAX_IDS
                || inspected.stream().anyMatch(java.util.Objects::isNull)
                || vanilla.stream().anyMatch(java.util.Objects::isNull)
                || extended.stream().anyMatch(java.util.Objects::isNull)
                || spiders.stream().anyMatch(java.util.Objects::isNull)) return false;
        if (!inspected.containsAll(vanilla) || !inspected.containsAll(extended)
                || !inspected.containsAll(spiders)) return false;
        for (Object id : vanilla) if (extended.contains(id) || spiders.contains(id)) return false;
        for (Object id : extended) if (spiders.contains(id)) return false;
        return true;
    }
    private NeoForgeRendererReportRules() {}
}
