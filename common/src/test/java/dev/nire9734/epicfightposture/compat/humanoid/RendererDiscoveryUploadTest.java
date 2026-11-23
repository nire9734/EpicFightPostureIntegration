package dev.nire9734.epicfightposture.compat.humanoid;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Pure tests: ids are plain strings, so no game classes are required. */
class RendererDiscoveryUploadTest {
    private static final Function<String, String> WIRE = Function.identity();
    private static final String FINGERPRINT = "a".repeat(64);
    private static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID NONCE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private static Set<String> ids(String prefix, int count) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < count; i++) ids.add(prefix + ":entity_with_a_realistic_name_" + i);
        return ids;
    }

    @Test
    void encodedSizeMatchesFriendlyByteBufWriteUtf() {
        assertEquals(1, RendererDiscoveryUpload.encodedUtfSize(""));
        assertEquals(17, RendererDiscoveryUpload.encodedUtfSize("minecraft:zombie"));
        assertEquals(2 + 200, RendererDiscoveryUpload.encodedUtfSize("x".repeat(200)));
        assertEquals(1 + 3, RendererDiscoveryUpload.encodedUtfSize("あ"));
    }

    @Test
    void smallReportIsOneChunk() {
        List<RendererDiscoveryUpload.Chunk<String>> chunks = RendererDiscoveryUpload.split(
                Set.of("minecraft:zombie", "minecraft:spider"), Set.of("minecraft:zombie"), Set.of(),
                Set.of("minecraft:spider"), WIRE, RendererDiscoveryUpload.CHUNK_ID_BYTE_BUDGET);
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(0).count());
        assertEquals(List.of("minecraft:spider", "minecraft:zombie"), chunks.get(0).inspected());
    }

    @Test
    void emptyReportStillProducesOneChunk() {
        List<RendererDiscoveryUpload.Chunk<String>> chunks = RendererDiscoveryUpload.split(
                Set.of(), Set.of(), Set.of(), Set.of(), WIRE, RendererDiscoveryUpload.CHUNK_ID_BYTE_BUDGET);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).inspected().isEmpty());
    }

    @Test
    void largeModpackReportIsSplitWithinTheByteBudgetAndReassembles() {
        // ~3000 living entity types, far beyond a single 32767-byte serverbound payload.
        Set<String> inspected = ids("bigpack", 3000);
        Set<String> humanoid = new HashSet<>();
        Set<String> spider = new HashSet<>();
        int n = 0;
        for (String id : inspected) {
            if (n % 3 == 0) humanoid.add(id);
            else if (n % 7 == 0) spider.add(id);
            n++;
        }

        List<RendererDiscoveryUpload.Chunk<String>> chunks = RendererDiscoveryUpload.split(
                inspected, humanoid, Set.of(), spider, WIRE, RendererDiscoveryUpload.CHUNK_ID_BYTE_BUDGET);
        assertTrue(chunks.size() > 1);
        for (RendererDiscoveryUpload.Chunk<String> chunk : chunks) {
            int bytes = 0;
            for (List<String> part : List.of(chunk.inspected(), chunk.vanillaHumanoid(),
                    chunk.extendedHumanoid(), chunk.vanillaSpider())) {
                for (String id : part) bytes += RendererDiscoveryUpload.encodedUtfSize(id);
            }
            assertTrue(bytes <= RendererDiscoveryUpload.CHUNK_ID_BYTE_BUDGET, "chunk bytes " + bytes);
            assertEquals(chunks.size(), chunk.count());
        }

        // Deliver out of order to prove ordering does not matter.
        List<RendererDiscoveryUpload.Chunk<String>> shuffled = new ArrayList<>(chunks);
        Collections.reverse(shuffled);
        RendererDiscoveryUpload.Assembler<String> assembler = new RendererDiscoveryUpload.Assembler<>();
        RendererDiscoveryUpload.Result<String> result = null;
        for (int i = 0; i < shuffled.size(); i++) {
            result = deliver(assembler, NONCE, shuffled.get(i));
            assertEquals(i == shuffled.size() - 1
                    ? RendererDiscoveryUpload.Status.COMPLETE
                    : RendererDiscoveryUpload.Status.INCOMPLETE, result.status());
        }
        assertEquals(inspected, result.report().inspected());
        assertEquals(humanoid, result.report().vanillaHumanoid());
        assertEquals(spider, result.report().vanillaSpider());
        assertTrue(result.report().extendedHumanoid().isEmpty());
        assertEquals(FINGERPRINT, result.report().fingerprint());
        assertFalse(assembler.hasPending(OPERATOR));
    }

    @Test
    void reportsNeedingTooManyChunksAreRefusedByTheClient() {
        assertThrows(IllegalArgumentException.class, () -> RendererDiscoveryUpload.split(
                ids("a", 200), Set.of(), Set.of(), Set.of(), WIRE, 50));
        assertThrows(IllegalArgumentException.class, () -> RendererDiscoveryUpload.split(
                ids("a", RendererDiscoveryUpload.MAX_IDS_PER_CATEGORY + 1), Set.of(), Set.of(), Set.of(),
                WIRE, RendererDiscoveryUpload.CHUNK_ID_BYTE_BUDGET));
    }

    @Test
    void duplicateAndInconsistentChunksAreRejected() {
        RendererDiscoveryUpload.Assembler<String> assembler = new RendererDiscoveryUpload.Assembler<>();
        assertEquals(RendererDiscoveryUpload.Status.INCOMPLETE, accept(assembler, NONCE, FINGERPRINT, 0, 2).status());
        assertEquals(RendererDiscoveryUpload.Status.REJECTED, accept(assembler, NONCE, FINGERPRINT, 0, 2).status());
        assertFalse(assembler.hasPending(OPERATOR));

        accept(assembler, NONCE, FINGERPRINT, 0, 2);
        assertEquals(RendererDiscoveryUpload.Status.REJECTED, accept(assembler, NONCE, FINGERPRINT, 1, 3).status());

        accept(assembler, NONCE, FINGERPRINT, 0, 2);
        assertEquals(RendererDiscoveryUpload.Status.REJECTED,
                accept(assembler, NONCE, "b".repeat(64), 1, 2).status());
    }

    @Test
    void invalidChunkHeadersAreRejected() {
        RendererDiscoveryUpload.Assembler<String> assembler = new RendererDiscoveryUpload.Assembler<>();
        assertEquals(RendererDiscoveryUpload.Status.REJECTED, accept(assembler, NONCE, FINGERPRINT, 0, 0).status());
        assertEquals(RendererDiscoveryUpload.Status.REJECTED, accept(assembler, NONCE, FINGERPRINT, 2, 2).status());
        assertEquals(RendererDiscoveryUpload.Status.REJECTED, accept(assembler, NONCE, FINGERPRINT, -1, 2).status());
        assertEquals(RendererDiscoveryUpload.Status.REJECTED,
                accept(assembler, NONCE, FINGERPRINT, 0, RendererDiscoveryUpload.MAX_CHUNKS + 1).status());
        assertEquals(RendererDiscoveryUpload.Status.REJECTED, accept(assembler, NONCE, null, 0, 1).status());
    }

    @Test
    void aNewNonceSupersedesAnUnfinishedReport() {
        RendererDiscoveryUpload.Assembler<String> assembler = new RendererDiscoveryUpload.Assembler<>();
        accept(assembler, NONCE, FINGERPRINT, 0, 2);
        UUID newer = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
        assertEquals(RendererDiscoveryUpload.Status.COMPLETE, accept(assembler, newer, FINGERPRINT, 0, 1).status());
        assertFalse(assembler.hasPending(OPERATOR));
    }

    @Test
    void accumulatedCategoriesAreBounded() {
        RendererDiscoveryUpload.Assembler<String> assembler = new RendererDiscoveryUpload.Assembler<>();
        List<String> half = new ArrayList<>(ids("x", RendererDiscoveryUpload.MAX_IDS_PER_CATEGORY / 2 + 1));
        List<String> other = new ArrayList<>(ids("y", RendererDiscoveryUpload.MAX_IDS_PER_CATEGORY / 2 + 1));
        assertEquals(RendererDiscoveryUpload.Status.INCOMPLETE, assembler.accept(OPERATOR, NONCE, FINGERPRINT,
                0, 3, half, List.of(), List.of(), List.of()).status());
        assertEquals(RendererDiscoveryUpload.Status.REJECTED, assembler.accept(OPERATOR, NONCE, FINGERPRINT,
                1, 3, other, List.of(), List.of(), List.of()).status());
    }

    @Test
    void sessionsCanBeCheckedWithoutConsumingTheNonce() {
        RendererDiscoverySessions sessions = new RendererDiscoverySessions();
        UUID nonce = sessions.issue(OPERATOR);
        assertTrue(sessions.isPending(OPERATOR, nonce));
        assertTrue(sessions.isPending(OPERATOR, nonce));
        assertFalse(sessions.isPending(OPERATOR, NONCE));
        assertTrue(sessions.consume(OPERATOR, nonce));
        assertFalse(sessions.isPending(OPERATOR, nonce));
    }

    private static RendererDiscoveryUpload.Result<String> deliver(
            RendererDiscoveryUpload.Assembler<String> assembler, UUID nonce,
            RendererDiscoveryUpload.Chunk<String> chunk) {
        return assembler.accept(OPERATOR, nonce, FINGERPRINT, chunk.index(), chunk.count(),
                chunk.inspected(), chunk.vanillaHumanoid(), chunk.extendedHumanoid(), chunk.vanillaSpider());
    }

    private static RendererDiscoveryUpload.Result<String> accept(
            RendererDiscoveryUpload.Assembler<String> assembler, UUID nonce, String fingerprint,
            int index, int count) {
        return assembler.accept(OPERATOR, nonce, fingerprint, index, count,
                List.of("minecraft:zombie"), List.of(), List.of(), List.of());
    }
}
