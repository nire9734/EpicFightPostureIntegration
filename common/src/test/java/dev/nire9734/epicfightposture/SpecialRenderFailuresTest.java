package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialRenderFailuresTest {
    private static final Object ZOMBIE = new Object();
    private static final Object SKELETON = new Object();

    @AfterEach
    void clear() {
        SpecialRenderFailures.clear();
    }

    @Test
    void firstFailureDisablesTheTypeAndIsReportedOnce() {
        assertFalse(SpecialRenderFailures.isDisabled(ZOMBIE));
        assertTrue(SpecialRenderFailures.disable(ZOMBIE));
        assertFalse(SpecialRenderFailures.disable(ZOMBIE), "only the first failure is logged");
        assertTrue(SpecialRenderFailures.isDisabled(ZOMBIE));
        assertFalse(SpecialRenderFailures.isDisabled(SKELETON), "other types keep special rendering");
    }

    @Test
    void rendererRebuildReenablesEveryType() {
        SpecialRenderFailures.disable(ZOMBIE);
        SpecialRenderFailures.clear();
        assertFalse(SpecialRenderFailures.isDisabled(ZOMBIE));
    }

    @Test
    void nullTypesAreIgnored() {
        assertFalse(SpecialRenderFailures.disable(null));
        assertFalse(SpecialRenderFailures.isDisabled(null));
    }
}
