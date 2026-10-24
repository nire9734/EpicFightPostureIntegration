package dev.nire9734.epicfightposture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Loader-neutral, read-only admission diagnostics for encountered mobs.
 * One row per entity type reflects its most recent observation; repeat changes
 * are tracked without writing files on combat or entity-tracking hot paths.
 */
public final class PostureCoverageReport {
    private static final long WRITE_INTERVAL_NS = TimeUnit.SECONDS.toNanos(3);

    private final String loader;
    private final Map<String, Seen> seen = new TreeMap<>();
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
        Seen previous = seen.get(key);
        if (previous == null) {
            seen.put(key, new Seen(sample, false));
            dirty = true;
        } else if (!previous.latest.equals(sample)) {
            seen.put(key, new Seen(sample, true));
            dirty = true;
        }
    }

    /** Clears observations between integrated or dedicated server sessions. */
    public synchronized void reset() {
        seen.clear();
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
        Path output = directory.resolve(loader + "-posture-coverage.tsv");
        Path temporary = Files.createTempFile(directory, "posture-coverage-", ".tmp");
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
        StringBuilder tsv = new StringBuilder("# EFPI posture coverage v1\n");
        tsv.append("# loader: ").append(loader).append('\n');
        tsv.append("# scope: observed living entities only; latest result per entity type\n");
        tsv.append("# legacy: admission checks before Animator/armature preflight; diagnostic only\n");
        tsv.append("# current: admission checks used by the server; does not guarantee CE execution\n");
        tsv.append("entity_id\tlegacy_eligible\tcurrent_status\tcurrent_reason\tcomparison\tpatch_class\tvariants_seen\n");
        for (var entry : seen.entrySet()) {
            Sample sample = entry.getValue().latest;
            tsv.append(entry.getKey()).append('\t')
                    .append(sample.legacyEligible).append('\t')
                    .append(sample.status).append('\t')
                    .append(sample.reason).append('\t')
                    .append(comparison(sample)).append('\t')
                    .append(sample.patchType).append('\t')
                    .append(entry.getValue().varied).append('\n');
        }
        return tsv.toString();
    }

    private static String comparison(Sample sample) {
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
    private record Seen(Sample latest, boolean varied) {}
}
