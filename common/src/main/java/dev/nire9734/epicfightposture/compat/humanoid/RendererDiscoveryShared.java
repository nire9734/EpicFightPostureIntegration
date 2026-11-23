package dev.nire9734.epicfightposture.compat.humanoid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/** Loader-independent renderer discovery cache name and report validation. */
public final class RendererDiscoveryShared {
    public static final String CACHE_FILE_NAME = "renderer-discovery.json";
    /** Prevent rapid resource reloads from repeatedly refreshing all loaded mobs. */
    public static final int RELOAD_REQUEST_COOLDOWN_TICKS = 20;
    private static final int MAX_REPORT_IDS = 8192;
    /** Player-facing prefix: the published mod name, never an abbreviation. */
    private static final String CHAT_PREFIX = "[Epic Fight Posture Integration]";

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

    /** Translation-key prefix for operator chat notices; the lang files hold the text. */
    public static final String NOTICE_KEY_PREFIX = "epicfight_posture_integration.discovery.";
    public static final String FAILED_KEY = NOTICE_KEY_PREFIX + "failed";
    public static final String SEPARATOR_KEY = NOTICE_KEY_PREFIX + "separator";

    /** One counted item of an operator notice, e.g. "3 existing mobs patched". */
    public record NoticeDetail(String key, int count) {
        public String translationKey() {
            return NOTICE_KEY_PREFIX + key;
        }
    }

    /**
     * Language-neutral operator notice. The server sends it as translatable
     * chat components, so each operator reads it in their own client language.
     */
    public record OperatorNotice(boolean problems, List<NoticeDetail> details) {
        public OperatorNotice {
            details = List.copyOf(details);
        }

        public String headlineKey() {
            return NOTICE_KEY_PREFIX + (problems ? "attention" : "updated");
        }

        /** English text matching en_us.json, for tests and diagnostics. */
        public String english() {
            StringJoiner joined = new StringJoiner(", ");
            for (NoticeDetail detail : details) {
                joined.add(String.format(Locale.ROOT, ENGLISH.get(detail.key()), detail.count()));
            }
            return String.format(Locale.ROOT, problems ? ENGLISH_ATTENTION : ENGLISH_UPDATED, joined);
        }
    }

    private static final String ENGLISH_UPDATED = CHAT_PREFIX + " Compatibility updated: %s.";
    private static final String ENGLISH_ATTENTION =
            CHAT_PREFIX + " Compatibility update needs attention: %s. See server log.";
    private static final Map<String, String> ENGLISH = Map.of(
            "enabled", "%d mob types enabled",
            "patched", "%d existing mobs patched",
            "disabled", "%d mob types disabled",
            "conflicts", "%d renderer conflicts",
            "skipped", "%d registrations skipped",
            "failures", "%d patch failures");

    /** At most one short notice to the OP whose report changed server behavior; null if nothing changed. */
    public static OperatorNotice changeNotice(
            int newlyReady,
            int withdrawn,
            int patchedMobs,
            int newConflicts,
            int newlySkipped,
            int failedPatches) {
        List<NoticeDetail> details = new ArrayList<>();
        if (newlyReady > 0) details.add(new NoticeDetail("enabled", newlyReady));
        if (patchedMobs > 0) details.add(new NoticeDetail("patched", patchedMobs));
        if (withdrawn > 0) details.add(new NoticeDetail("disabled", withdrawn));
        if (newConflicts > 0) details.add(new NoticeDetail("conflicts", newConflicts));
        if (newlySkipped > 0) details.add(new NoticeDetail("skipped", newlySkipped));
        if (failedPatches > 0) details.add(new NoticeDetail("failures", failedPatches));
        if (details.isEmpty()) return null;
        boolean problems = newConflicts > 0 || newlySkipped > 0 || failedPatches > 0;
        return new OperatorNotice(problems, details);
    }

    /** English text of the failure notice (key {@link #FAILED_KEY}), for tests and diagnostics. */
    public static String reportErrorNotice() {
        return CHAT_PREFIX + " Renderer discovery failed. See server log.";
    }

    private RendererDiscoveryShared() {
    }
}
