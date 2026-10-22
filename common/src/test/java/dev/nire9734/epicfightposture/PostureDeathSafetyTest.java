package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevent death animations from being replaced by delayed posture breaks. */
class PostureDeathSafetyTest {
    @Test
    void normalLivingTargetsMayAccumulatePosture() {
        assertTrue(PostureDeathSafety.canReceivePosture(true, 1.0F));
        assertTrue(PostureDeathSafety.canReceivePosture(true, 20.0F));
    }

    @Test
    void lethalHealthOrDeadEntityCannotAccumulatePosture() {
        assertFalse(PostureDeathSafety.canReceivePosture(true, 0.0F));
        assertFalse(PostureDeathSafety.canReceivePosture(true, -5.0F));
        assertFalse(PostureDeathSafety.canReceivePosture(false, 20.0F));
        assertFalse(PostureDeathSafety.canReceivePosture(false, 0.0F));
        assertFalse(PostureDeathSafety.canReceivePosture(true, Float.NaN));
        assertFalse(PostureDeathSafety.canReceivePosture(true, Float.POSITIVE_INFINITY));
    }

    @Test
    void ordinaryDeathsClearSpecialPostureRenderingState() {
        assertTrue(PostureDeathSafety.shouldForgetDeadState(false, 0.0F, false));
        assertTrue(PostureDeathSafety.shouldForgetDeadState(true, 0.0F, false));
    }

    @Test
    void executionKillingBlowPreservesExecutedAnimationUntilCeFinishes() {
        assertFalse(PostureDeathSafety.shouldForgetDeadState(false, 0.0F, true));
        assertFalse(PostureDeathSafety.shouldForgetDeadState(true, 0.0F, true));
        assertTrue(PostureDeathSafety.shouldForgetDeadState(false, 0.0F, false));
    }

    @Test
    void AliveEntitiesNeverGetDeathCleanup() {
        assertFalse(PostureDeathSafety.shouldForgetDeadState(true, 20.0F, false));
        assertFalse(PostureDeathSafety.shouldForgetDeadState(true, 20.0F, true));
    }
}
