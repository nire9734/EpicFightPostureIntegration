package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Assassinations do not require prior posture damage. The server adapter
 * must be able to create a fresh state and send a BROKEN/EXECUTING snapshot.
 */
class NeoForgeAssassinationStateTest {
    @Test
    void freshTargetWithoutPreviousHitsBecomesVisibleDuringAssassination() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        assertEquals(0, state.completedBreaks);
        assertFalse(state.isBrokenForHud());
        state.startExecution();
        assertEquals(PostureState.EXECUTING, state.postureState);
        assertTrue(state.isBrokenForHud());
        assertEquals(0.0F, state.current);
        assertEquals(0, state.completedBreaks);
        state.beginRecovery(60);
        assertFalse(state.isBrokenForHud());
        assertEquals(PostureState.RECOVERING, state.postureState);
    }

    @Test
    void assassinatingAfterAnEarlierHitKeepsExistingBreakResistance() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        state.completedBreaks = 2;
        state.current = 45.0F;
        state.startExecution();
        assertEquals(2, state.completedBreaks);
        assertTrue(state.isLocked());
        assertEquals(0.0F, state.current);
    }
}
