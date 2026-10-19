package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostureBalanceTest {
    private static final float EPS = 0.0001F;

    @Test
    void impactEfficiencyIsBounded() {
        assertEquals(0.0F, PostureBalance.impactEfficiency(0.0F), EPS);
        assertEquals(0.0F, PostureBalance.impactEfficiency(Float.NaN), EPS);
        assertEquals(0.873205F, PostureBalance.impactEfficiency(1.0F), EPS);
        assertEquals(1.0F, PostureBalance.impactEfficiency(3.0F), EPS);
        assertEquals(1.247723F, PostureBalance.impactEfficiency(10.0F), EPS);
        assertEquals(1.5F, PostureBalance.impactEfficiency(1000.0F), EPS);
    }

    @Test
    void finalHpLossUsesIntegralFinisherCurve() {
        assertEquals(13.5F, attack(10, 100, 100, 100, 3, 2.7F), EPS);
        assertEquals(4.21875F, attack(10, 100, 100, 50, 3, 2.7F), EPS);
    }

    @Test
    void splitHitsAreEquivalentToOneLargeHit() {
        float one = attack(20, 100, 100, 80, 3, 2.7F);
        float parts = attack(20, 100, 100, 30, 3, 2.7F)
                + attack(20, 100, 70, 25, 3, 2.7F)
                + attack(20, 100, 45, 25, 3, 2.7F);
        assertEquals(one, parts, EPS);
    }

    @Test
    void sameFractionalHpLossMeansSameNormalizedPostureAcrossLevelScaling() {
        float low = attack(3.2F, 20, 20, 4, 3, 2.7F) / 3.2F;
        float high = attack(60.8F, 1214, 1214, 242.8F, 3, 2.7F) / 60.8F;
        assertEquals(low, high, EPS);
    }

    @Test
    void overkillIsClippedAndZeroDamageIsIgnored() {
        assertEquals(attack(10, 100, 10, 10, 3, 2.7F),
                attack(10, 100, 10, 1000, 3, 2.7F), EPS);
        assertEquals(0.0F, attack(10, 100, 100, 0, 3, 2.7F), EPS);
        assertEquals(0.0F, attack(10, 100, 100, 50, 0, 2.7F), EPS);
        assertEquals(0.0F, attack(10, 0, 100, 50, 3, 2.7F), EPS);
        assertEquals(0.0F, attack(10, 100, 100, Float.NaN, 3, 2.7F), EPS);
        assertEquals(0.0F, attack(10, 100, 100, 50, 3, 0), EPS);
    }

    @Test
    void finisherWithImpactThreeBreaksBetweenTwentyAndTenPercentHp() {
        assertTrue(2.7 * (.25 * .85 + .25 * Math.pow(.85, 3)) >= 1.0);
        assertTrue(2.7 * (.25 * .80 + .25 * Math.pow(.80, 3)) < 1.0);
    }

    @Test
    void innateEhpKeepsLeveledZombiesParryVulnerable() {
        float zombie = PostureBalance.parryPostureFraction(0.06F, 20, 20, 0.7F)
                * PostureBalance.healthPostureDamageMultiplier(1, .25F, 1, 2, 20, 40, 200);
        float boss = PostureBalance.parryPostureFraction(0.06F, 200, 20, 0.7F)
                * PostureBalance.healthPostureDamageMultiplier(1, .25F, 1, 2, 200, 40, 200);
        assertEquals(.12F, zombie, EPS);
        assertTrue(boss < .02F);
        assertTrue(zombie > boss * 6);
    }

    @Test
    void parryFinisherStillPenalizesInnatelyDurableBosses() {
        assertEquals(.25F, PostureBalance.healthPostureDamageMultiplier(
                1, .25F, 1, 2, 500, 40, 200), EPS);
        assertEquals(1.0F, PostureBalance.healthPostureDamageMultiplier(
                1, .25F, 1, 2, 20, 40, 200), EPS);
        assertEquals(.5F, PostureBalance.healthScalingStrength(120, 40, 200), EPS);
        assertEquals(0.0F, PostureBalance.parryDamage(0, .06F, 20, 20, .7F), EPS);
    }

    @Test
    void regenerationDelayRemainsTenSeconds() {
        assertEquals(200, PostureBalance.regenDelayTicks(10.0F));
        assertEquals(0, PostureBalance.regenDelayTicks(0.0F));
    }

    private static float attack(float basePosture, float maxHp, float hpBefore,
                                float hpDamage, float impact, float coefficient) {
        return PostureBalance.attackPostureDamage(
                basePosture, maxHp, hpBefore, hpDamage, impact, coefficient);
    }
}
