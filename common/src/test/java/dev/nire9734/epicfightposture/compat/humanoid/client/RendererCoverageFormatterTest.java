package dev.nire9734.epicfightposture.compat.humanoid.client;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RendererCoverageFormatterTest {
    @Test
    void forgeAndNeoForgeUseTheSameSortedRows() {
        Map<String, String> results = new LinkedHashMap<>();
        results.put("minecraft:zombie", "verified:vanilla");
        results.put("cataclysm:ignis", "verified:lionfish");
        results.put("minecraft:spider", "verified:spider");

        String rows = "entity_id\tresult\n"
                + "cataclysm:ignis\tverified:lionfish\n"
                + "minecraft:spider\tverified:spider\n"
                + "minecraft:zombie\tverified:vanilla\n";
        assertEquals("# EFPI renderer coverage v1\n# loader: forge\n" + rows,
                RendererCoverageFormatter.format("forge", results));
        assertEquals("# EFPI renderer coverage v1\n# loader: neoforge\n" + rows,
                RendererCoverageFormatter.format("neoforge", results));
    }

    @Test
    void nullEntriesAreSkippedAndStatusCannotInsertExtraRows() {
        Map<String, String> results = new LinkedHashMap<>();
        results.put(null, "verified:vanilla");
        results.put("minecraft:zombie", null);
        results.put("minecraft:skeleton", "rejected:\tbad\nline\rbreak");

        assertEquals("# EFPI renderer coverage v1\n# loader: forge\n"
                + "entity_id\tresult\n"
                + "minecraft:skeleton\trejected:_bad_line_break\n",
                RendererCoverageFormatter.format("forge", results));
    }

    @Test
    void unknownLoaderIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RendererCoverageFormatter.format("fabric", Map.of()));
    }
}
