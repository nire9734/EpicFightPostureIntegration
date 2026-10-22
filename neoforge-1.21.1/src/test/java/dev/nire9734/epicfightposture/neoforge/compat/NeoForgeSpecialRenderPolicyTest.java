package dev.nire9734.epicfightposture.neoforge.compat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeoForgeSpecialRenderPolicyTest {
    @Test void defaultsToVanillaUntilAllRequirementsPass() {
        assertFalse(NeoForgeSpecialRenderPolicy.shouldOverride(false, true, true, true));
        assertFalse(NeoForgeSpecialRenderPolicy.shouldOverride(true, false, true, true));
        assertFalse(NeoForgeSpecialRenderPolicy.shouldOverride(true, true, false, true));
        assertFalse(NeoForgeSpecialRenderPolicy.shouldOverride(true, true, true, false));
        assertTrue(NeoForgeSpecialRenderPolicy.shouldOverride(true, true, true, true));
    }

    @Test void unverifiedGiantNeverOverridesItsScaleSensitiveRenderer() {
        assertFalse(NeoForgeSpecialRenderPolicy.shouldOverride(true, true, false, true));
    }
}
