package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NeoForgeCompatibilityDamageTest {
    @Test
    void fallbackMatchesForge302Bounds() {
        assertEquals(0.5F, NeoForgeCompatibilityDamage.fallbackImpact(1.0F));
        assertEquals(3.0F, NeoForgeCompatibilityDamage.fallbackImpact(12.0F));
        assertEquals(5.0F, NeoForgeCompatibilityDamage.fallbackImpact(100.0F));
    }

    @Test
    void excludedDamageTypesMatchForge302() {
        assertFalse(NeoForgeCompatibilityDamage.isFallbackEligible("minecraft:on_fire"));
        assertFalse(NeoForgeCompatibilityDamage.isFallbackEligible("out_of_world"));
        assertFalse(NeoForgeCompatibilityDamage.isFallbackEligible("some_mod:bleed"));
        assertTrue(NeoForgeCompatibilityDamage.isFallbackEligible("player"));
        assertTrue(NeoForgeCompatibilityDamage.isFallbackEligible("minecraft:mob_attack"));
        assertTrue(NeoForgeCompatibilityDamage.isFallbackEligible(null));
    }
}
