package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;
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
}
