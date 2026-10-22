package dev.nire9734.epicfightposture.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostureHudPolicyTest {
    @Test
    void alwaysDisplaysEvenWhenUndamaged() {
        assertTrue(PostureHudPolicy.shouldDisplay(PostureHudPolicy.Visibility.ALWAYS,
                20.0F, 20.0F, 0, false));
    }

    @Test
    void hiddenNeverDisplaysEvenWhenDamaged() {
        assertFalse(PostureHudPolicy.shouldDisplay(PostureHudPolicy.Visibility.HIDDEN,
                1.0F, 20.0F, 10, true));
    }

    @Test
    void damagedOnlyUsesHitHealthAndPosture() {
        assertFalse(PostureHudPolicy.shouldDisplay(PostureHudPolicy.Visibility.DAMAGED_ONLY,
                20.0F, 20.0F, 0, false));
        assertTrue(PostureHudPolicy.shouldDisplay(PostureHudPolicy.Visibility.DAMAGED_ONLY,
                19.0F, 20.0F, 0, false));
        assertTrue(PostureHudPolicy.shouldDisplay(PostureHudPolicy.Visibility.DAMAGED_ONLY,
                20.0F, 20.0F, 0, true));
        assertTrue(PostureHudPolicy.shouldDisplay(PostureHudPolicy.Visibility.DAMAGED_ONLY,
                20.0F, 20.0F, 1, false));
    }

    @Test
    void undamagedMobIsHidden() {
        assertFalse(PostureHudPolicy.hasCombatDamage(20.0F, 20.0F, 0, false));
    }

    @Test
    void healthDamageTriggersVisibility() {
        assertTrue(PostureHudPolicy.hasCombatDamage(19.0F, 20.0F, 0, false));
    }

    @Test
    void postureDamageTriggersVisibilityEvenWithoutHealthLoss() {
        assertTrue(PostureHudPolicy.hasCombatDamage(20.0F, 20.0F, 0, true));
    }

    @Test
    void recentHitTriggersVisibilityEvenWhenDamageIsAbsorbed() {
        assertTrue(PostureHudPolicy.hasCombatDamage(20.0F, 20.0F, 10, false));
    }

    @Test
    void mobBecomesHiddenAfterFullRecovery() {
        assertFalse(PostureHudPolicy.hasCombatDamage(20.0F, 20.0F, 0, false));
    }
}
