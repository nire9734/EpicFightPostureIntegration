package dev.nire9734.epicfightposture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Both loaders use this parser at config load/reload, not in a mob tick. */
class PostureConfigEntriesTest {
    /** A pure test double; the loaders supply ResourceLocation::tryParse. */
    private static String id(String raw) {
        String[] parts = raw.split(":", -1);
        if (parts.length > 2) return null;
        String namespace = parts.length == 2 ? parts[0] : "minecraft";
        String path = parts.length == 2 ? parts[1] : parts[0];
        if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+")) return null;
        return namespace + ":" + path;
    }

    @Test
    void blacklistNormalizesWhitespaceAndDefaultNamespace() {
        List<String> rejected = new ArrayList<>();
        Set<String> ids = PostureConfigEntries.entityBlacklist(
                Arrays.asList(" minecraft:zombie ", "skeleton", "Bad ID", "", null,
                        "minecraft:zombie"), PostureConfigEntriesTest::id, rejected::add);

        assertEquals(Set.of(id("minecraft:zombie"), id("minecraft:skeleton")), ids);
        assertEquals(Arrays.asList("Bad ID", "", null), rejected);
    }

    @Test
    void zeroMultiplierIsValidAndLastDuplicateWins() {
        List<String> rejected = new ArrayList<>();
        Map<String, Float> overrides = PostureConfigEntries.entityMultipliers(
                List.of(" cataclysm:ignis = 0.25 ", "minecraft:zombie=2.0",
                        "cataclysm:ignis=0.75", "cataclysm:ignis=0"), PostureConfigEntriesTest::id, rejected::add);

        assertEquals(0.0F, overrides.get(id("cataclysm:ignis")).floatValue());
        assertEquals(2.0F, overrides.get(id("minecraft:zombie")).floatValue());
        assertTrue(rejected.isEmpty());
    }

    @Test
    void attackAndParryOverrideListsRemainIndependent() {
        Map<String, Float> attack = PostureConfigEntries.entityMultipliers(
                List.of("cataclysm:ignis=2.0"), PostureConfigEntriesTest::id,
                raw -> fail("Unexpected invalid attack override: " + raw));
        Map<String, Float> parry = PostureConfigEntries.entityMultipliers(
                List.of("cataclysm:ignis=0.5"), PostureConfigEntriesTest::id,
                raw -> fail("Unexpected invalid parry override: " + raw));

        assertEquals(2.0F, attack.get(id("cataclysm:ignis")).floatValue());
        assertEquals(0.5F, parry.get(id("cataclysm:ignis")).floatValue());
        assertFalse(attack.containsKey(id("minecraft:zombie")));
        assertFalse(parry.containsKey(id("minecraft:zombie")));
        assertEquals(2.0F, PostureConfigEntries.multiplierFor(id("cataclysm:ignis"), attack, 1.5F));
        assertEquals(0.5F, PostureConfigEntries.multiplierFor(id("cataclysm:ignis"), parry, 3.0F));
        assertEquals(1.5F, PostureConfigEntries.multiplierFor(id("minecraft:zombie"), attack, 1.5F));
        assertEquals(3.0F, PostureConfigEntries.multiplierFor(null, parry, 3.0F));
    }

    @Test
    void invalidOverridesNeverReplacePreviouslyAcceptedValues() {
        List<String> rejected = new ArrayList<>();
        Map<String, Float> overrides = PostureConfigEntries.entityMultipliers(
                Arrays.asList("cataclysm:ignis=1.5", "cataclysm:ignis=NaN",
                        "cataclysm:ignis=Infinity", "cataclysm:ignis=101", "cataclysm:ignis=-0.001",
                        "minecraft:zombie=", "Bad ID=2", "missing separator", null),
                PostureConfigEntriesTest::id, rejected::add);

        assertEquals(Map.of(id("cataclysm:ignis"), 1.5F), overrides);
        assertEquals(8, rejected.size());
    }
}
