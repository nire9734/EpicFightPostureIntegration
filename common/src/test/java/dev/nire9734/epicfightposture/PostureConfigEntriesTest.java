package dev.nire9734.epicfightposture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Both loaders use this parser at config load/reload, not in a mob tick. */
class PostureConfigEntriesTest {
    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    @Test
    void blacklistNormalizesWhitespaceAndDefaultNamespace() {
        List<String> rejected = new ArrayList<>();
        Set<ResourceLocation> ids = PostureConfigEntries.entityBlacklist(
                Arrays.asList(" minecraft:zombie ", "skeleton", "Bad ID", "", null,
                        "minecraft:zombie"), rejected::add);

        assertEquals(Set.of(id("minecraft:zombie"), id("minecraft:skeleton")), ids);
        assertEquals(Arrays.asList("Bad ID", "", null), rejected);
    }

    @Test
    void multiplierReplacesGlobalAndLastValidDuplicateWins() {
        List<String> rejected = new ArrayList<>();
        Map<ResourceLocation, Float> overrides = PostureConfigEntries.entityMultipliers(
                List.of(" cataclysm:ignis = 0.25 ", "minecraft:zombie=2.0",
                        "cataclysm:ignis=0.75", "cataclysm:ignis=0"), rejected::add);

        assertEquals(0.75F, overrides.get(id("cataclysm:ignis")));
        assertEquals(2.0F, overrides.get(id("minecraft:zombie")));
        assertEquals(List.of("cataclysm:ignis=0"), rejected);
    }

    @Test
    void invalidOverridesNeverReplacePreviouslyAcceptedValues() {
        List<String> rejected = new ArrayList<>();
        Map<ResourceLocation, Float> overrides = PostureConfigEntries.entityMultipliers(
                Arrays.asList("cataclysm:ignis=1.5", "cataclysm:ignis=NaN",
                        "cataclysm:ignis=Infinity", "cataclysm:ignis=101", "cataclysm:ignis=0.001",
                        "minecraft:zombie=", "Bad ID=2", "missing separator", null), rejected::add);

        assertEquals(Map.of(id("cataclysm:ignis"), 1.5F), overrides);
        assertEquals(8, rejected.size());
    }
}
