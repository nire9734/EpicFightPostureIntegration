package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardPosturePolicyTest {
    @Test
    void onlyPaidNormalGuardsWithNonParryWeaponsGeneratePressure() {
        assertTrue(GuardPosturePolicy.shouldApply(true, false, false));
        assertFalse(GuardPosturePolicy.shouldApply(true, false, true));
        assertFalse(GuardPosturePolicy.shouldApply(true, true, false));
        assertFalse(GuardPosturePolicy.shouldApply(true, true, true));
        assertFalse(GuardPosturePolicy.shouldApply(false, false, false));
    }

    @Test
    void meleeDefenseIgnoresTheProjectileMultiplier() {
        assertEquals(1.0F, GuardPosturePolicy.defenseMultiplier(1.0F, false, 0.0F));
        assertEquals(0.25F, GuardPosturePolicy.defenseMultiplier(0.25F, false, 0.0F));
        assertEquals(0.25F, GuardPosturePolicy.defenseMultiplier(0.25F, false, Float.NaN));
    }

    @Test
    void projectileDefenseIsOffByDefaultAndScalesWhenEnabled() {
        // Default config: projectile_defense_posture_multiplier = 0.0
        assertEquals(0.0F, GuardPosturePolicy.defenseMultiplier(1.0F, true, 0.0F));
        assertEquals(0.0F, GuardPosturePolicy.defenseMultiplier(0.25F, true, 0.0F));
        assertEquals(1.0F, GuardPosturePolicy.defenseMultiplier(1.0F, true, 1.0F));
        assertEquals(0.125F, GuardPosturePolicy.defenseMultiplier(0.25F, true, 0.5F));
    }

    @Test
    void invalidProjectileMultipliersDisableProjectilePressure() {
        assertEquals(0.0F, GuardPosturePolicy.defenseMultiplier(1.0F, true, -1.0F));
        assertEquals(0.0F, GuardPosturePolicy.defenseMultiplier(1.0F, true, Float.NaN));
        assertEquals(0.0F, GuardPosturePolicy.defenseMultiplier(1.0F, true, Float.POSITIVE_INFINITY));
    }
}
