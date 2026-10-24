package dev.nire9734.epicfightposture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Loader-neutral, read-only diagnostics of registry entries, renderer scans,
 * eligibility and real guard-break/execution outcomes. No I/O on combat paths.
 */
public final class PostureCoverageReport {
    private static final long WRITE_INTERVAL_NS = TimeUnit.SECONDS.toNanos(3);

    private final String loader;
    private final Map<String, Seen> seen = new TreeMap<>();
    private final Map<String, Renderer> renderers = new TreeMap<>();
    private final Map<String, String> modVersions = new TreeMap<>();
    private String clientHudVisibility = "NOT_REPORTED";
    private Instant sessionStarted = Instant.now();
    private boolean dirty;
    private long nextWriteNanos;

    public PostureCoverageReport(String loader) {
        if (!"forge".equals(loader) && !"neoforge".equals(loader)) {
            throw new IllegalArgumentException("Unknown posture coverage loader: " + loader);
        }
        this.loader = loader;
    }

    public synchronized void observe(String entityId, boolean legacyEligible,
                                     String status, String reason, String patchType) {
        if (entityId == null || entityId.isBlank()) return;
        Sample sample = new Sample(legacyEligible, safe(status), safe(reason), safe(patchType));
        String key = safe(entityId);
        Seen previous = entry(key);
        if (previous.latest == null || !previous.latest.equals(sample)) {
            previous.varied |= previous.latest != null;
            previous.latest = sample;
            dirty = true;
        }
    }

    /** Registry presence alone is not evidence of executable posture support. */
    public synchronized void registerEntityIds(Collection<String> ids) {
        if (ids == null) return;
        for (String id : ids) {
            if (id != null && !id.isBlank() && !entry(id).registered) {
                entry(id).registered = true;
                dirty = true;
            }
        }
    }

    public synchronized void setModVersions(Map<String, String> versions) {
        Map<String, String> clean = new TreeMap<>();
        if (versions != null) versions.forEach((id, value) -> {
            if (id != null && !id.isBlank()) clean.put(safe(id), safe(value));
        });
        if (!clean.equals(modVersions)) {
            modVersions.clear();
            modVersions.putAll(clean);
            dirty = true;
        }
    }

    public synchronized void setClientHudVisibility(String visibility) {
        String clean = safe(visibility);
        if (!clean.equals(clientHudVisibility)) {
            clientHudVisibility = clean;
            dirty = true;
        }
    }

    /** Replace one source's complete renderer scan (never authorizes patch registration). */
    public synchronized void replaceRendererScan(String source, Map<String, String> results) {
        if (!"client".equals(source) && !"trusted_op".equals(source)) return;
        renderers.entrySet().removeIf(item -> source.equals(item.getValue().source));
        if (results != null) results.forEach((id, value) -> {
            if (id != null && !id.isBlank() && value != null) {
                renderers.put(safe(id), new Renderer(safe(value), source));
            }
        });
        for (Seen value : seen.values()) value.renderer = null;
        renderers.forEach((id, renderer) -> entry(id).renderer = renderer);
        dirty = true;
    }

    /** Record actual NEUTRALIZE attempts, not a speculative animation test. */
    public synchronized void observeGuardBreak(String entityId, boolean confirmed, String reason) {
        if (entityId == null || entityId.isBlank()) return;
        Seen value = entry(entityId);
        if (confirmed) value.guardBreakConfirmed++;
        else {
            value.guardBreakFailed++;
            value.guardBreakLastFailure = safe(reason);
        }
        dirty = true;
    }

    /** CE refusals before OnExecutionStart cannot be observed by this reporter. */
    public synchronized void observeExecution(String entityId, String event) {
        if (entityId == null || entityId.isBlank()) return;
        Seen value = entry(entityId);
        switch (event) {
            case "STARTED" -> value.executionStarted++;
            case "CONFIRMED" -> value.executionConfirmed++;
            case "TIMEOUT" -> value.executionTimeout++;
            default -> { return; }
        }
        dirty = true;
    }

    private Seen entry(String entityId) {
        String id = safe(entityId);
        return seen.computeIfAbsent(id, ignored -> {
            Seen value = new Seen();
            value.renderer = renderers.get(id);
            return value;
        });
    }

    /** Clears observations between integrated or dedicated server sessions. */
    public synchronized void reset() {
        seen.clear();
        sessionStarted = Instant.now();
        // Client scans may precede an integrated server's startup; keep these.
        renderers.entrySet().removeIf(item -> !"client".equals(item.getValue().source));
        renderers.forEach((id, renderer) -> entry(id).renderer = renderer);
        modVersions.clear();
        dirty = true; // Replace a stale report with an empty, labelled report.
        nextWriteNanos = 0;
    }

    public synchronized boolean flushIfDue(Path configDirectory) throws IOException {
        if (!dirty || (nextWriteNanos != 0 && System.nanoTime() - nextWriteNanos < 0)) {
            return false;
        }
        flush(configDirectory);
        return true;
    }

    public synchronized void flush(Path configDirectory) throws IOException {
        if (!dirty) return;
        nextWriteNanos = System.nanoTime() + WRITE_INTERVAL_NS;
        Path directory = configDirectory.resolve("epicfight_posture_integration").resolve("cache");
        Files.createDirectories(directory);
        Path output = directory.resolve(loader + "-diagnostics.tsv");
        Path temporary = Files.createTempFile(directory, "efpi-diagnostics-", ".tmp");
        try {
            Files.writeString(temporary, format(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public synchronized String format() {
        StringBuilder tsv = new StringBuilder("# EFPI diagnostics v2\n");
        tsv.append("# loader: ").append(loader).append('\n');
        tsv.append("# session_started_utc: ").append(sessionStarted).append('\n');
        tsv.append("# generated_utc: ").append(Instant.now()).append('\n');
        tsv.append("# scope: entity registry + latest client/OP renderer scan + observed server posture checks\n");
        tsv.append("# NOT_OBSERVED: no server eligibility check yet; registry includes non-living entities\n");
        tsv.append("# legacy: admission checks before Animator/armature preflight; diagnostic only\n");
        tsv.append("# current: admission checks used by the server; does not guarantee CE execution\n");
        tsv.append("# execution: CE refusal before its start event cannot be observed; timeout means start was unconfirmed\n");
        tsv.append("# client_hud_visibility_at_scan: ").append(clientHudVisibility).append('\n');
        tsv.append("# mods: ");
        boolean first = true;
        for (var mod : modVersions.entrySet()) {
            if (!first) tsv.append(';');
            tsv.append(mod.getKey()).append('@').append(mod.getValue());
            first = false;
        }
        tsv.append('\n');
        tsv.append("entity_id\tlegacy_eligible\tcurrent_status\tcurrent_reason\tcomparison\tpatch_class\tvariants_seen\tregistered\trenderer_result\trenderer_source\tguard_break_confirmed\tguard_break_failed\tguard_break_last_failure\texecution_started\texecution_confirmed\texecution_timeout\n");
        for (var entry : seen.entrySet()) {
            Seen value = entry.getValue();
            Sample sample = value.latest;
            tsv.append(entry.getKey()).append('\t')
                    .append(sample == null ? "NOT_OBSERVED" : sample.legacyEligible).append('\t')
                    .append(sample == null ? "NOT_OBSERVED" : sample.status).append('\t')
                    .append(sample == null ? "NOT_OBSERVED" : sample.reason).append('\t')
                    .append(comparison(sample)).append('\t')
                    .append(sample == null ? "none" : sample.patchType).append('\t')
                    .append(value.varied).append('\t')
                    .append(value.registered).append('\t')
                    .append(value.renderer == null ? "NOT_SCANNED" : value.renderer.result).append('\t')
                    .append(value.renderer == null ? "none" : value.renderer.source).append('\t')
                    .append(value.guardBreakConfirmed).append('\t')
                    .append(value.guardBreakFailed).append('\t')
                    .append(value.guardBreakLastFailure).append('\t')
                    .append(value.executionStarted).append('\t')
                    .append(value.executionConfirmed).append('\t')
                    .append(value.executionTimeout).append('\n');
        }
        return tsv.toString();
    }

    private static String comparison(Sample sample) {
        if (sample == null) return "not_tested";
        if ("PENDING".equals(sample.status)) return "pending";
        boolean currentEligible = "SUPPORTED".equals(sample.status);
        if (sample.legacyEligible && !currentEligible) return "newly_excluded";
        if (!sample.legacyEligible && currentEligible) return "newly_supported";
        return "unchanged";
    }

    private static String safe(String text) {
        return text == null ? "" : text.replace('\t', '_').replace('\n', '_').replace('\r', '_');
    }

    private record Sample(boolean legacyEligible, String status, String reason, String patchType) {}
    private record Renderer(String result, String source) {}

    private static final class Seen {
        private Sample latest;
        private boolean varied;
        private boolean registered;
        private Renderer renderer;
        private long guardBreakConfirmed;
        private long guardBreakFailed;
        private String guardBreakLastFailure = "none";
        private long executionStarted;
        private long executionConfirmed;
        private long executionTimeout;
    }
}
