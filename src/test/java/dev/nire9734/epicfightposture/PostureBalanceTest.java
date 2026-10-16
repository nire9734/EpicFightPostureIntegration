package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostureBalanceTest {
    private static final float EPSILON = 0.0001F;

    @Test
    void parryDamageUsesTenPercentWithHalfPointMinimum() {
        assertEquals(0.5F, PostureBalance.parryDamage(3.2F), EPSILON);
        assertEquals(1.0F, PostureBalance.parryDamage(10.0F), EPSILON);
        assertEquals(10.0F, PostureBalance.parryDamage(100.0F), EPSILON);
    }

    @Test
    void regenCurveMatchesHealthAnchors() {
        assertEquals(0.12F, PostureBalance.regenFractionPerSecond(1.00F), EPSILON);
        assertEquals(0.10F, PostureBalance.regenFractionPerSecond(0.75F), EPSILON);
        assertEquals(0.07F, PostureBalance.regenFractionPerSecond(0.50F), EPSILON);
        assertEquals(0.03F, PostureBalance.regenFractionPerSecond(0.25F), EPSILON);
        assertEquals(0.03F, PostureBalance.regenFractionPerSecond(0.00F), EPSILON);
    }

    @Test
    void regenCurveInterpolatesContinuouslyBetweenAnchors() {
        assertEquals(0.11F, PostureBalance.regenFractionPerSecond(0.875F), EPSILON);
        assertEquals(0.085F, PostureBalance.regenFractionPerSecond(0.625F), EPSILON);
        assertEquals(0.05F, PostureBalance.regenFractionPerSecond(0.375F), EPSILON);
    }

    @Test
    void regenCurveClampsOutOfRangeHealth() {
        assertEquals(0.12F, PostureBalance.regenFractionPerSecond(2.0F), EPSILON);
        assertEquals(0.03F, PostureBalance.regenFractionPerSecond(-1.0F), EPSILON);
    }
}
