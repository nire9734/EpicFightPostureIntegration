package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostureBalanceTest {
    private static final float EPSILON = 0.0001F;

    @Test
    void parryDamageUsesConfiguredFractionWithHalfPointMinimum() {
        assertEquals(0.5F, PostureBalance.parryDamage(3.2F, 0.06F), EPSILON);
        assertEquals(0.6F, PostureBalance.parryDamage(10.0F, 0.06F), EPSILON);
        assertEquals(6.0F, PostureBalance.parryDamage(100.0F, 0.06F), EPSILON);
    }

    @Test
    void healthPostureDamageMultiplierScalesLinearlyFromFullToZeroHealth() {
        assertEquals(1.0F, PostureBalance.healthPostureDamageMultiplier(1.00F, 0.50F), EPSILON);
        assertEquals(1.125F, PostureBalance.healthPostureDamageMultiplier(0.75F, 0.50F), EPSILON);
        assertEquals(1.25F, PostureBalance.healthPostureDamageMultiplier(0.50F, 0.50F), EPSILON);
        assertEquals(1.375F, PostureBalance.healthPostureDamageMultiplier(0.25F, 0.50F), EPSILON);
        assertEquals(1.50F, PostureBalance.healthPostureDamageMultiplier(0.00F, 0.50F), EPSILON);
    }

    @Test
    void healthPostureDamageMultiplierClampsHealthAndSanitizesBonus() {
        assertEquals(1.0F, PostureBalance.healthPostureDamageMultiplier(2.0F, 0.50F), EPSILON);
        assertEquals(1.50F, PostureBalance.healthPostureDamageMultiplier(-1.0F, 0.50F), EPSILON);
        assertEquals(1.0F, PostureBalance.healthPostureDamageMultiplier(Float.NaN, 0.50F), EPSILON);
        assertEquals(1.0F, PostureBalance.healthPostureDamageMultiplier(0.0F, -1.0F), EPSILON);
    }

    @Test
    void regenDelayConvertsSecondsToTicks() {
        assertEquals(100, PostureBalance.regenDelayTicks(5.0F));
        assertEquals(0, PostureBalance.regenDelayTicks(0.0F));
        assertEquals(0, PostureBalance.regenDelayTicks(Float.NaN));
    }

    @Test
    void regenUsesOneConfiguredConstantRate() {
        assertEquals(0.04F, PostureBalance.regenFractionPerSecond(0.04F), EPSILON);
        assertEquals(0.0F, PostureBalance.regenFractionPerSecond(-1.0F), EPSILON);
        assertEquals(1.0F, PostureBalance.regenFractionPerSecond(2.0F), EPSILON);
        assertEquals(0.0F, PostureBalance.regenFractionPerSecond(Float.NaN), EPSILON);
    }
}
