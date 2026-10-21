package dev.nire9734.epicfightposture.compat.humanoid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression guards for the generic datapack-humanoid fallback policy. */
class HumanoidNeutralizeFallbackPolicyTest {
    @Test
    void fillsMissingNeutralizeForSupportedHumanoidWithOrdinaryHitReaction() {
        assertTrue(HumanoidNeutralizeFallbackPolicy.shouldInstall(true, false, true, false));
    }

    @Test
    void preservesDatapackNeutralizeWhenAlreadyConfigured() {
        assertFalse(HumanoidNeutralizeFallbackPolicy.shouldInstall(true, true, true, false));
    }

    @Test
    void doesNotApplyBipedAnimationToNonHumanoidArmature() {
        assertFalse(HumanoidNeutralizeFallbackPolicy.shouldInstall(false, false, true, false));
    }

    @Test
    void doesNotForceStunOnMobWithoutOrdinaryHitReaction() {
        assertFalse(HumanoidNeutralizeFallbackPolicy.shouldInstall(true, false, false, false));
    }

    @Test
    void honorsExplicitBlacklist() {
        assertFalse(HumanoidNeutralizeFallbackPolicy.shouldInstall(true, false, true, true));
    }

    @Test
    void falseIfMultipleDisqualifyingConditions() {
        assertFalse(HumanoidNeutralizeFallbackPolicy.shouldInstall(false, true, false, true));
    }
}
