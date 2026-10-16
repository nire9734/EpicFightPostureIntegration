package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostureBalanceTest {
    private static final float EPSILON = 0.0001F;
    private static final float LOW_REGEN = 0.015F;
    private static final float MID_REGEN = 0.04F;
    private static final float HIGH_REGEN = 0.06F;
    private static final float FULL_REGEN = 0.08F;

    @Test
    void parryDamageUsesTenPercentWithHalfPointMinimum() {
        assertEquals(0.5F, PostureBalance.parryDamage(3.2F), EPSILON);
        assertEquals(1.0F, PostureBalance.parryDamage(10.0F), EPSILON);
        assertEquals(10.0F, PostureBalance.parryDamage(100.0F), EPSILON);
    }

    @Test
    void regenDelayConvertsSecondsToTicks() {
        assertEquals(100, PostureBalance.regenDelayTicks(5.0F));
        assertEquals(0, PostureBalance.regenDelayTicks(0.0F));
        assertEquals(0, PostureBalance.regenDelayTicks(Float.NaN));
    }

    @Test
    void regenCurveMatchesHealthAnchors() {
        assertEquals(FULL_REGEN, regen(1.00F), EPSILON);
        assertEquals(HIGH_REGEN, regen(0.75F), EPSILON);
        assertEquals(MID_REGEN, regen(0.50F), EPSILON);
        assertEquals(LOW_REGEN, regen(0.25F), EPSILON);
        assertEquals(LOW_REGEN, regen(0.00F), EPSILON);
    }

    @Test
    void regenCurveInterpolatesContinuouslyBetweenAnchors() {
        assertEquals(0.07F, regen(0.875F), EPSILON);
        assertEquals(0.05F, regen(0.625F), EPSILON);
        assertEquals(0.0275F, regen(0.375F), EPSILON);
    }

    @Test
    void regenCurveClampsOutOfRangeHealth() {
        assertEquals(FULL_REGEN, regen(2.0F), EPSILON);
        assertEquals(LOW_REGEN, regen(-1.0F), EPSILON);
    }

    private static float regen(float healthFraction) {
        return PostureBalance.regenFractionPerSecond(
                healthFraction,
                LOW_REGEN,
                MID_REGEN,
                HIGH_REGEN,
                FULL_REGEN
        );
    }
}
