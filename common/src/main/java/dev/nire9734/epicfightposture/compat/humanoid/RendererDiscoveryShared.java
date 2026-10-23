package dev.nire9734.epicfightposture.compat.humanoid;

import java.util.Objects;
import java.util.Set;

/** Loader-independent renderer discovery cache name and operator notices. */
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

    public static String rejectionMessage(ValidationError error) {
        return switch (error) {
            case INVALID_FINGERPRINT -> "EFPI: rejected invalid renderer discovery fingerprint.";
            case INCONSISTENT -> "EFPI: rejected inconsistent renderer discovery report.";
            case AMBIGUOUS -> "EFPI: rejected ambiguous renderer discovery report.";
            case NONE -> throw new IllegalArgumentException("No report validation error");
        };
    }

    public static String cacheFullMessage() {
        return "EFPI: renderer discovery report cache is full; report ignored.";
    }

    public static String restartRequiredMessage(
            int vanillaHumanoids,
            int extendedHumanoids,
            int vanillaSpiders,
            int conflicts) {
        int confirmed = vanillaHumanoids + extendedHumanoids + vanillaSpiders;
        return "EFPI: renderer discovery: "
                + confirmed + " confirmed ("
                + vanillaHumanoids + " vanilla humanoid, "
                + extendedHumanoids + " extended humanoid, "
                + vanillaSpiders + " vanilla SpiderModel), "
                + conflicts + " conflict(s)."
                + " Restart the server to apply the pending compatibility list.";
    }

    private RendererDiscoveryShared() {
    }
}
