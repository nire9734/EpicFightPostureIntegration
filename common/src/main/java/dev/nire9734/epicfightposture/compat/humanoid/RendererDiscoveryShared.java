package dev.nire9734.epicfightposture.compat.humanoid;

import java.util.Objects;
import java.util.Set;

/** Loader-independent renderer discovery cache name and report validation. */
public final class RendererDiscoveryShared {
    public static final String CACHE_FILE_NAME = "renderer-discovery.json";
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

    private RendererDiscoveryShared() {
    }
}
