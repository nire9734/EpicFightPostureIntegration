package dev.nire9734.epicfightposture.compat.humanoid;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Loader-independent splitting and reassembly of an operator's renderer
 * discovery report. Minecraft limits a client-to-server custom payload to
 * 32767 bytes, so a large modpack's report is sent as several chunks that
 * share the server-issued nonce. The server only accumulates chunks for a
 * nonce it issued, and validates the reassembled report exactly as before.
 *
 * Generic over the id type so the same logic serves Forge and NeoForge and
 * can be unit-tested without game classes.
 */
public final class RendererDiscoveryUpload {
    /** Upper bound on chunks per report; also bounds server-side buffering. */
    public static final int MAX_CHUNKS = 64;
    /** Encoded-id budget per chunk; leaves headroom for headers under 32767 bytes. */
    public static final int CHUNK_ID_BYTE_BUDGET = 24_000;
    /** Same per-category limit as a complete report. */
    public static final int MAX_IDS_PER_CATEGORY = 8192;

    /** One wire chunk. Index is zero-based; every chunk of a report repeats the count. */
    public record Chunk<T>(int index, int count, List<T> inspected, List<T> vanillaHumanoid,
                           List<T> extendedHumanoid, List<T> vanillaSpider) {
        public Chunk {
            inspected = List.copyOf(inspected);
            vanillaHumanoid = List.copyOf(vanillaHumanoid);
            extendedHumanoid = List.copyOf(extendedHumanoid);
            vanillaSpider = List.copyOf(vanillaSpider);
        }
    }

    /** A fully reassembled report, ready for RendererDiscoveryReportProcessor. */
    public record Report<T>(String fingerprint, Set<T> inspected, Set<T> vanillaHumanoid,
                            Set<T> extendedHumanoid, Set<T> vanillaSpider) {
        public Report {
            inspected = Set.copyOf(inspected);
            vanillaHumanoid = Set.copyOf(vanillaHumanoid);
            extendedHumanoid = Set.copyOf(extendedHumanoid);
            vanillaSpider = Set.copyOf(vanillaSpider);
        }
    }

    public enum Status { INCOMPLETE, COMPLETE, REJECTED }

    public record Result<T>(Status status, Report<T> report, String rejection) {
        static <T> Result<T> incomplete() { return new Result<>(Status.INCOMPLETE, null, null); }
        static <T> Result<T> complete(Report<T> report) { return new Result<>(Status.COMPLETE, report, null); }
        static <T> Result<T> rejected(String reason) { return new Result<>(Status.REJECTED, null, reason); }
    }

    /** Wire size of a string written with FriendlyByteBuf#writeUtf: VarInt byte length plus UTF-8 bytes. */
    public static int encodedUtfSize(String value) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        return varIntSize(bytes) + bytes;
    }

    static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    /**
     * Splits the four categories into chunks whose encoded ids stay within the
     * byte budget. Ids are sorted for deterministic output. An empty report is
     * still one chunk, so the server always receives a reply.
     *
     * @throws IllegalArgumentException if the report would need more than {@link #MAX_CHUNKS}
     *         chunks or a category exceeds {@link #MAX_IDS_PER_CATEGORY}
     */
    public static <T> List<Chunk<T>> split(Set<T> inspected, Set<T> vanillaHumanoid,
                                           Set<T> extendedHumanoid, Set<T> vanillaSpider,
                                           Function<T, String> toWire, int byteBudget) {
        List<List<T>> categories = List.of(
                sorted(inspected, toWire), sorted(vanillaHumanoid, toWire),
                sorted(extendedHumanoid, toWire), sorted(vanillaSpider, toWire));
        List<List<List<T>>> chunks = new ArrayList<>();
        List<List<T>> current = emptyChunk();
        int used = 0;
        for (int category = 0; category < categories.size(); category++) {
            for (T id : categories.get(category)) {
                int size = encodedUtfSize(toWire.apply(id));
                if (used > 0 && used + size > byteBudget) {
                    chunks.add(current);
                    current = emptyChunk();
                    used = 0;
                }
                current.get(category).add(id);
                used += size;
            }
        }
        chunks.add(current);
        if (chunks.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("Renderer discovery report needs " + chunks.size()
                    + " chunks; the limit is " + MAX_CHUNKS);
        }

        int count = chunks.size();
        List<Chunk<T>> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            List<List<T>> parts = chunks.get(index);
            result.add(new Chunk<>(index, count, parts.get(0), parts.get(1), parts.get(2), parts.get(3)));
        }
        return List.copyOf(result);
    }

    private static <T> List<T> sorted(Set<T> ids, Function<T, String> toWire) {
        if (ids.size() > MAX_IDS_PER_CATEGORY) {
            throw new IllegalArgumentException("Too many entity ids in a renderer discovery category: "
                    + ids.size());
        }
        List<T> list = new ArrayList<>(ids);
        list.sort(Comparator.comparing(toWire));
        return list;
    }

    private static <T> List<List<T>> emptyChunk() {
        return List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Server-side reassembly. One in-flight report per operator; a chunk for a
     * new nonce replaces the old buffer. The caller must check that the nonce
     * is the one it issued before calling {@link #accept}.
     */
    public static final class Assembler<T> {
        private final Map<UUID, Pending<T>> pending = new HashMap<>();

        public synchronized Result<T> accept(UUID operator, UUID nonce, String fingerprint,
                                             int index, int count, Collection<T> inspected,
                                             Collection<T> vanillaHumanoid,
                                             Collection<T> extendedHumanoid,
                                             Collection<T> vanillaSpider) {
            if (operator == null || nonce == null || fingerprint == null) {
                pending.remove(operator);
                return Result.rejected("missing header");
            }
            if (count < 1 || count > MAX_CHUNKS || index < 0 || index >= count) {
                pending.remove(operator);
                return Result.rejected("invalid chunk " + index + "/" + count);
            }

            Pending<T> report = pending.get(operator);
            if (report == null || !report.nonce.equals(nonce)) {
                report = new Pending<>(nonce, fingerprint, count);
                pending.put(operator, report);
            } else if (report.count != count || !report.fingerprint.equals(fingerprint)) {
                pending.remove(operator);
                return Result.rejected("inconsistent chunk header");
            }
            if (!report.received.add(index)) {
                pending.remove(operator);
                return Result.rejected("duplicate chunk " + index);
            }

            report.inspected.addAll(inspected);
            report.vanillaHumanoid.addAll(vanillaHumanoid);
            report.extendedHumanoid.addAll(extendedHumanoid);
            report.vanillaSpider.addAll(vanillaSpider);
            if (report.inspected.size() > MAX_IDS_PER_CATEGORY
                    || report.vanillaHumanoid.size() > MAX_IDS_PER_CATEGORY
                    || report.extendedHumanoid.size() > MAX_IDS_PER_CATEGORY
                    || report.vanillaSpider.size() > MAX_IDS_PER_CATEGORY) {
                pending.remove(operator);
                return Result.rejected("too many entity ids");
            }

            if (report.received.size() < report.count) {
                return Result.incomplete();
            }
            pending.remove(operator);
            return Result.complete(new Report<>(report.fingerprint, report.inspected,
                    report.vanillaHumanoid, report.extendedHumanoid, report.vanillaSpider));
        }

        public synchronized void clear(UUID operator) {
            pending.remove(operator);
        }

        synchronized boolean hasPending(UUID operator) {
            return pending.containsKey(operator);
        }
    }

    private static final class Pending<T> {
        final UUID nonce;
        final String fingerprint;
        final int count;
        final Set<Integer> received = new HashSet<>();
        final Set<T> inspected = new HashSet<>();
        final Set<T> vanillaHumanoid = new HashSet<>();
        final Set<T> extendedHumanoid = new HashSet<>();
        final Set<T> vanillaSpider = new HashSet<>();

        Pending(UUID nonce, String fingerprint, int count) {
            this.nonce = nonce;
            this.fingerprint = fingerprint;
            this.count = count;
        }
    }

    private RendererDiscoveryUpload() {
    }
}
