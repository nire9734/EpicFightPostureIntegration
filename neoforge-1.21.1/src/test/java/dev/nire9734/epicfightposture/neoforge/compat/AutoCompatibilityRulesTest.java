package dev.nire9734.epicfightposture.neoforge.compat;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutoCompatibilityRulesTest {
    @Test void automaticCompatibilityCanBeDisabled() {
        assertFalse(AutoCompatibilityRules.allows("minecraft:bogged", false,
                List.of(), List.of(), List.of()));
    }

    @Test void explicitBlacklistWinsForVanilla() {
        assertFalse(AutoCompatibilityRules.allows("minecraft:giant", true,
                List.of("minecraft:giant"), List.of(), List.of()));
    }

    @Test void vanillaCandidatesIgnoreModAllowAndDenyLists() {
        assertTrue(AutoCompatibilityRules.allows("minecraft:bogged", true,
                List.of(), List.of("cataclysm"), List.of("minecraft")));
    }

    @Test void denylistOverridesAllowlistForModdedCandidates() {
        assertFalse(AutoCompatibilityRules.allows("example:knight", true,
                List.of(), List.of("example"), List.of("example")));
    }

    @Test void nonemptyAllowlistFiltersOtherMods() {
        assertFalse(AutoCompatibilityRules.allows("other:monster", true,
                List.of(), List.of("example"), List.of()));
        assertTrue(AutoCompatibilityRules.allows("example:knight", true,
                List.of(), List.of("example"), List.of()));
    }

    @Test void rejectsInvalidEntityIds() {
        assertFalse(AutoCompatibilityRules.allows("broken", true,
                List.of(), List.of(), List.of()));
        assertFalse(AutoCompatibilityRules.allows(null, true,
                List.of(), List.of(), List.of()));
    }
}
