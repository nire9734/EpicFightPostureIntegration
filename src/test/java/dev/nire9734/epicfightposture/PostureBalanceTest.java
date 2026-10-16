package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostureBalanceTest {
    private static final float EPSILON = 0.0001F;
    private static final float LOW_REGEN = 0.01F;
    private static final float MID_REGEN = 0.03F;
    private static final float HIGH_REGEN = 0.06F;
    private static final float FULL_REGEN = 0.08F;

    @Test
    void parryDamageUsesConfiguredFractionWithHalfPointMinimum() {
        assertEquals(0.5F, PostureBalance.parryDamage(3.2F, 0.06F), EPSILON);
        assertEquals(0.6F, PostureBalance.parryDamage(10.0F, 0.06F), EPSILON);
        assertEquals(6.0F, PostureBalance.parryDamage(100.0F, 0.06F), EPSILON);
    }

    @Test
    void attackDamageScaleMatchesEhpStyleSublinearScaling() {
        assertEquals(1.0F, PostureBalance.attackDamageScale(8.0F, 8.0F, 0.7F), EPSILON);
        assertEquals((float) Math.pow(2.0D, 0.7D),
                PostureBalance.attackDamageScale(16.0F, 8.0F, 0.7F), EPSILON);
        assertEquals((float) Math.pow(4.0D, 0.7D),
                PostureBalance.attackDamageScale(32.0F, 8.0F, 0.7F), EPSILON);
    }

    @Test
    void attackDamageScaleCanBeDisabledAndSanitizesInvalidInputs() {
        assertEquals(1.0F, PostureBalance.attackDamageScale(64.0F, 8.0F, 0.0F), EPSILON);
        assertEquals(0.0F, PostureBalance.attackDamageScale(0.0F, 8.0F, 0.7F), EPSILON);
        assertEquals(0.0F, PostureBalance.attackDamageScale(Float.NaN, 8.0F, 0.7F), EPSILON);
        assertEquals(1.0F, PostureBalance.attackDamageScale(8.0F, Float.NaN, 0.7F), EPSILON);
    }

    @Test
    void healthPostureDamageMultiplierUsesConfiguredQuadraticCurve() {
        assertEquals(0.25F, damageMultiplier(1.00F), EPSILON);
        assertEquals(0.296875F, damageMultiplier(0.75F), EPSILON);
        assertEquals(0.4375F, damageMultiplier(0.50F), EPSILON);
        assertEquals(0.671875F, damageMultiplier(0.25F), EPSILON);
        assertEquals(1.00F, damageMultiplier(0.00F), EPSILON);
    }

    @Test
    void healthPostureDamageMultiplierClampsHealthAndSanitizesCurve() {
        assertEquals(0.25F, damageMultiplier(2.0F), EPSILON);
        assertEquals(1.00F, damageMultiplier(-1.0F), EPSILON);
        assertEquals(0.25F, damageMultiplier(Float.NaN), EPSILON);
        assertEquals(0.625F,
                PostureBalance.healthPostureDamageMultiplier(0.50F, 0.25F, 1.00F, Float.NaN),
                EPSILON);
    }

    @Test
    void regenDelayConvertsTenSecondsToTicks() {
        assertEquals(200, PostureBalance.regenDelayTicks(10.0F));
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
        assertEquals(0.045F, regen(0.625F), EPSILON);
        assertEquals(0.02F, regen(0.375F), EPSILON);
    }

    @Test
    void regenCurveClampsOutOfRangeHealth() {
        assertEquals(FULL_REGEN, regen(2.0F), EPSILON);
        assertEquals(LOW_REGEN, regen(-1.0F), EPSILON);
    }

    private static float damageMultiplier(float healthFraction) {
        return PostureBalance.healthPostureDamageMultiplier(
                healthFraction,
                0.25F,
                1.00F,
                2.00F
        );
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
