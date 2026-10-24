package dev.nire9734.epicfightposture.compat.humanoid;

import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/** Loader-independent renderer discovery cache name and report validation. */
public final class RendererDiscoveryShared {
    public static final String CACHE_FILE_NAME = "renderer-discovery.json";
    /** Prevent rapid resource reloads from repeatedly refreshing all loaded mobs. */
    public static final int RELOAD_REQUEST_COOLDOWN_TICKS = 20;
    private static final int MAX_REPORT_IDS = 8192;

    public enum ValidationError {
        NONE,
        INVALID_FINGERPRINT,
        INCONSISTENT,
        AMBIGUOUS
    }

    /** Same limits, subset checks and rejection reasons for both loaders. */
    public static ValidationError validateReport(String fingerprint, Set<?> inspected,
                                                 Set<?> vanilla, Set<?> extended, Set<?> spiders) {
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            return ValidationError.INVALID_FINGERPRINT;
        }
        if (invalidIds(inspected) || invalidIds(vanilla)
                || invalidIds(extended) || invalidIds(spiders)
                || !inspected.containsAll(vanilla)
                || !inspected.containsAll(extended)
                || !inspected.containsAll(spiders)) {
            return ValidationError.INCONSISTENT;
        }
        if (overlap(vanilla, extended) || overlap(vanilla, spiders) || overlap(extended, spiders)) {
            return ValidationError.AMBIGUOUS;
        }
        return ValidationError.NONE;
    }

    private static boolean invalidIds(Set<?> ids) {
        return ids == null || ids.size() > MAX_REPORT_IDS || ids.stream().anyMatch(Objects::isNull);
    }

    private static boolean overlap(Set<?> first, Set<?> second) {
        for (Object id : first) {
            if (second.contains(id)) return true;
        }
        return false;
    }

    /** At most one short notice to the OP whose report changed server behavior. */
    public static String changeNotice(
            int newlyReady,
            int withdrawn,
            int patchedMobs,
            int newConflicts,
            int newlySkipped,
            int failedPatches) {
        StringJoiner details = new StringJoiner(", ");
        if (newlyReady > 0) details.add(newlyReady + " mob types enabled");
        if (patchedMobs > 0) details.add(patchedMobs + " existing mobs patched");
        if (withdrawn > 0) details.add(withdrawn + " mob types disabled");
        if (newConflicts > 0) details.add(newConflicts + " renderer conflicts");
        if (newlySkipped > 0) details.add(newlySkipped + " registrations skipped");
        if (failedPatches > 0) details.add(failedPatches + " patch failures");
        if (details.length() == 0) return null;
        boolean problems = newConflicts > 0 || newlySkipped > 0 || failedPatches > 0;
        return "[EFPI] Compatibility " + (problems ? "update needs attention: " : "updated: ")
                + details + (problems ? ". See server log." : ".");
    }

    public static String reportErrorNotice() {
        return "[EFPI] Renderer discovery failed. See server log.";
    }

    private RendererDiscoveryShared() {
    }
}
