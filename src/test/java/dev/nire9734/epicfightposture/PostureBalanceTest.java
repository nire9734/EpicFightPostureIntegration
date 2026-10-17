package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostureBalanceTest {
    private static final float EPSILON = 0.0001F;

    @Test
    void parryFractionIncreasesContinuouslyTowardLowEhp() {
        assertEquals(0.160000F,
                PostureBalance.parryPostureFraction(0.08F, 20.0F, 20.0F, 0.7F), EPSILON);
        assertEquals(0.129246F,
                PostureBalance.parryPostureFraction(0.08F, 40.0F, 20.0F, 0.7F), EPSILON);
        assertEquals(0.105931F,
                PostureBalance.parryPostureFraction(0.08F, 100.0F, 20.0F, 0.7F), EPSILON);
        assertEquals(0.095962F,
                PostureBalance.parryPostureFraction(0.08F, 200.0F, 20.0F, 0.7F), EPSILON);
        assertEquals(0.088405F,
                PostureBalance.parryPostureFraction(0.08F, 500.0F, 20.0F, 0.7F), EPSILON);
    }

    @Test
    void parryDamageUsesEhpDerivedFractionWithoutAbsoluteMinimum() {
        assertEquals(0.512F,
                PostureBalance.parryDamage(3.2F, 0.08F, 20.0F, 20.0F, 0.7F), EPSILON);
        assertEquals(0.0F,
                PostureBalance.parryDamage(0.0F, 0.08F, 20.0F, 20.0F, 0.7F), EPSILON);
        assertEquals(0.0F,
                PostureBalance.parryDamage(Float.NaN, 0.08F, 20.0F, 20.0F, 0.7F), EPSILON);
    }

    @Test
    void parryScalingSanitizesInvalidEhpInputs() {
        assertEquals(0.16F,
                PostureBalance.parryPostureFraction(0.08F, Float.NaN, 20.0F, 0.7F), EPSILON);
        assertEquals(0.16F,
                PostureBalance.parryPostureFraction(0.08F, 20.0F, Float.NaN, 0.7F), EPSILON);
        assertEquals(0.16F,
                PostureBalance.parryPostureFraction(0.08F, 20.0F, 20.0F, Float.NaN), EPSILON);
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
    void ehpScalingUsesSmoothstepBetweenConfiguredAnchors() {
        assertEquals(0.0F, PostureBalance.healthScalingStrength(20.0F, 40.0F, 200.0F), EPSILON);
        assertEquals(0.0F, PostureBalance.healthScalingStrength(40.0F, 40.0F, 200.0F), EPSILON);
        assertEquals(0.5F, PostureBalance.healthScalingStrength(120.0F, 40.0F, 200.0F), EPSILON);
        assertEquals(1.0F, PostureBalance.healthScalingStrength(200.0F, 40.0F, 200.0F), EPSILON);
        assertEquals(1.0F, PostureBalance.healthScalingStrength(500.0F, 40.0F, 200.0F), EPSILON);
    }

    @Test
    void lowEhpMobsBlendOutHealthBasedFinisherPenalty() {
        assertEquals(1.0F, ehpDamageMultiplier(1.0F, 20.0F), EPSILON);
        assertEquals(0.625F, ehpDamageMultiplier(1.0F, 120.0F), EPSILON);
        assertEquals(0.25F, ehpDamageMultiplier(1.0F, 200.0F), EPSILON);
        assertEquals(1.0F, ehpDamageMultiplier(0.0F, 20.0F), EPSILON);
        assertEquals(1.0F, ehpDamageMultiplier(0.0F, 500.0F), EPSILON);
    }

    @Test
    void ehpScalingHandlesReversedOrCollapsedAnchors() {
        assertEquals(0.0F, PostureBalance.healthScalingStrength(20.0F, 200.0F, 40.0F), EPSILON);
        assertEquals(0.5F, PostureBalance.healthScalingStrength(120.0F, 200.0F, 40.0F), EPSILON);
        assertEquals(1.0F, PostureBalance.healthScalingStrength(200.0F, 200.0F, 40.0F), EPSILON);
        assertEquals(0.0F, PostureBalance.healthScalingStrength(39.0F, 40.0F, 40.0F), EPSILON);
        assertEquals(1.0F, PostureBalance.healthScalingStrength(40.0F, 40.0F, 40.0F), EPSILON);
    }

    @Test
    void regenDelayConvertsTenSecondsToTicks() {
        assertEquals(200, PostureBalance.regenDelayTicks(10.0F));
        assertEquals(0, PostureBalance.regenDelayTicks(0.0F));
        assertEquals(0, PostureBalance.regenDelayTicks(Float.NaN));
    }

    private static float damageMultiplier(float healthFraction) {
        return PostureBalance.healthPostureDamageMultiplier(
                healthFraction,
                0.25F,
                1.00F,
                2.00F
        );
    }

    private static float ehpDamageMultiplier(float healthFraction, float effectiveHealth) {
        return PostureBalance.healthPostureDamageMultiplier(
                healthFraction,
                0.25F,
                1.00F,
                2.00F,
                effectiveHealth,
                40.0F,
                200.0F
        );
    }
}
