package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostureRuntimeStateTest {
    private static final float EPS = 0.0001F;

    @Test void initialStateIsNormal() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        assertEquals(100.0F, state.current, EPS);
        assertTrue(state.acceptsPostureDamage());
        assertFalse(state.isBrokenForHud());
    }

    @Test void appliesResistanceAndResetsRegenerationDelay() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        assertFalse(state.applyDamage(30.0F, 1.5F, 300));
        assertEquals(80.0F, state.current, EPS);
        assertEquals(300, state.regenDelay);
        assertTrue(state.applyDamage(160.0F, 2.0F, 300));
        assertEquals(0.0F, state.current, EPS);
    }

    @Test void zeroDamageParryDoesNotRestartRegeneration() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        assertFalse(state.applyDamage(0.0F, 1.0F, 300));
        assertEquals(100.0F, state.current, EPS);
        assertEquals(0, state.regenDelay);
        state.applyDamage(30.0F, 1.0F, 300);
        for (int i = 0; i < 60; i++) state.advanceRegeneration(.02F);
        assertEquals(240, state.regenDelay);
        assertFalse(state.applyDamage(0.0F, 1.0F, 300));
        assertFalse(state.applyDamage(-5.0F, 1.0F, 300));
        assertFalse(state.applyDamage(Float.NaN, 1.0F, 300));
        assertFalse(state.applyDamage(10.0F, 0.0F, 300));
        assertEquals(240, state.regenDelay);
    }

    @Test void parryAfterAttackRestartsDelayFromLatestPostureReduction() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        state.applyDamage(20.0F, 1.0F, 300); // direct attack
        for (int i = 0; i < 200; i++) state.advanceRegeneration(.02F);
        assertEquals(100, state.regenDelay);
        assertFalse(state.applyDamage(10.0F, 1.0F, 300)); // parry, no HP loss
        assertEquals(70.0F, state.current, EPS);
        assertEquals(300, state.regenDelay);
        for (int i = 0; i < 300; i++) state.advanceRegeneration(.02F);
        assertEquals(70.0F, state.current, EPS);
        state.advanceRegeneration(.02F);
        assertEquals(70.1F, state.current, EPS);
    }

    @Test void onlyConfirmedBreaksIncreaseResistanceCount() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        state.prepareBreak(300);
        state.beginRecovery(60);
        assertEquals(0, state.completedBreaks);
        state.confirmBreak();
        assertEquals(1, state.completedBreaks);
        assertEquals(PostureState.BROKEN, state.postureState);
        assertFalse(state.acceptsPostureDamage());
        assertTrue(state.isBrokenForHud());
        state.completedBreaks = Integer.MAX_VALUE;
        state.confirmBreak();
        assertEquals(Integer.MAX_VALUE, state.completedBreaks);
    }

    @Test void executionStartAndActiveExecutionKeepStateLocked() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        state.prepareBreak(300);
        state.confirmBreak();
        state.enterExecution();
        assertEquals(PostureState.EXECUTING, state.postureState);
        assertEquals(300, state.regenDelay);
        state.startExecution();
        assertEquals(0, state.regenDelay);
        assertEquals(0, state.recoveryTicksRemaining);
        assertTrue(state.isLocked());
    }

    @Test void executionStartWaitsForScheduledTaskOnBothLoaders() {
        int startTick = 100;
        int deadline = startTick + PostureRuntimeState.EXECUTION_START_GRACE_TICKS;
        assertTrue(PostureRuntimeState.waitingForExecutionTask(startTick, deadline));
        assertTrue(PostureRuntimeState.waitingForExecutionTask(deadline, deadline));
        assertFalse(PostureRuntimeState.waitingForExecutionTask(deadline + 1, deadline));
        assertFalse(PostureRuntimeState.waitingForExecutionTask(startTick, null));
    }

    @Test void recoveryFinishesAfterExactlySixtyTicks() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        state.beginRecovery(60);
        assertFalse(state.acceptsPostureDamage());
        assertFalse(state.isBrokenForHud());
        for (int i = 0; i < 59; i++) assertFalse(state.advanceRecovery(60));
        assertEquals(100 * 59.0F / 60.0F, state.current, EPS);
        assertTrue(state.advanceRecovery(60));
        assertEquals(100.0F, state.current, EPS);
        assertEquals(0, state.recoveryTicksRemaining);
        assertEquals(PostureState.NORMAL, state.postureState);
    }

    @Test void ordinaryRegenerationStartsAfterFifteenSeconds() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        state.applyDamage(40, 1, 300);
        for (int i = 0; i < 300; i++) state.advanceRegeneration(.02F);
        assertEquals(60.0F, state.current, EPS);
        for (int i = 0; i < 20; i++) state.advanceRegeneration(.02F);
        assertEquals(62.0F, state.current, EPS);
        for (int i = 0; i < 1000; i++) state.advanceRegeneration(.02F);
        assertEquals(100.0F, state.current, EPS);
    }

    @Test void changedMaximumRetainsFractionOutsideLockedStates() {
        PostureRuntimeState state = new PostureRuntimeState(100.0F);
        state.current = 25.0F;
        state.updateMaximum(200.0F);
        assertEquals(50.0F, state.current, EPS);
        state.confirmBreak();
        state.current = 0.0F;
        state.updateMaximum(400.0F);
        assertEquals(0.0F, state.current, EPS);
        state.beginRecovery(60);
        state.advanceRecovery(60);
        float previous = state.current;
        state.updateMaximum(800.0F);
        assertEquals(previous * 2.0F, state.current, EPS);
    }
}
