package dev.nire9734.epicfightposture.neoforge;

import dev.nire9734.epicfightposture.PostureConfigPolicy;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure policy tests: NeoForge's test classpath does not include game classes. */
class NeoForgeServerSettingsTest {
    private static boolean blocked(String id, boolean efpiOwned, boolean automatic,
            Set<String> blacklist, Set<String> allow, Set<String> deny) {
        String namespace = id.substring(0, id.indexOf(':'));
        boolean allowed = PostureConfigPolicy.allowsAutomatic(automatic, namespace, allow, deny);
        return PostureConfigPolicy.blocksPosture(id, blacklist, efpiOwned, allowed);
    }

    @Test
    void disablingAutomaticCompatibilityOnlyExcludesEfpiOwnedMobs() {
        assertTrue(blocked("cataclysm:ignis", true, false, Set.of(), Set.of(), Set.of()));
        assertFalse(blocked("cataclysm:ignis", false, false, Set.of(), Set.of(), Set.of()));
        assertTrue(blocked("minecraft:zombie", true, false, Set.of(), Set.of(), Set.of()));
        assertFalse(blocked("minecraft:zombie", false, false, Set.of(), Set.of(), Set.of()));
    }

    @Test
    void explicitBlacklistExcludesBothOwnedAndExistingPatches() {
        assertTrue(blocked("cataclysm:ignis", true, true,
                Set.of("cataclysm:ignis"), Set.of(), Set.of()));
        assertTrue(blocked("cataclysm:ignis", false, true,
                Set.of("cataclysm:ignis"), Set.of(), Set.of()));
        assertFalse(blocked("minecraft:zombie", true, true,
                Set.of("cataclysm:ignis"), Set.of(), Set.of()));
    }

    @Test
    void modFiltersOnlyExcludeEfpiOwnedAndNeverExcludeVanilla() {
        assertTrue(blocked("cataclysm:ignis", true, true,
                Set.of(), Set.of("cataclysm"), Set.of("cataclysm")));
        assertFalse(blocked("cataclysm:ignis", false, true,
                Set.of(), Set.of("cataclysm"), Set.of("cataclysm")));
        assertFalse(blocked("minecraft:zombie", true, true,
                Set.of(), Set.of("cataclysm"), Set.of("cataclysm")));
    }
}
