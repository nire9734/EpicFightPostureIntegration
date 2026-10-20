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
        assertEquals(10.5F, attack(10, 100, 100, 100, 3, 2.1F), EPS);
        assertEquals(3.28125F, attack(10, 100, 100, 50, 3, 2.1F), EPS);
    }

    @Test
    void splitHitsAreEquivalentToOneLargeHit() {
        float one = attack(20, 100, 100, 80, 3, 2.1F);
        float parts = attack(20, 100, 100, 30, 3, 2.1F)
                + attack(20, 100, 70, 25, 3, 2.1F)
                + attack(20, 100, 45, 25, 3, 2.1F);
        assertEquals(one, parts, EPS);
    }

    @Test
    void sameFractionalHpLossMeansSameNormalizedPostureAcrossLevelScaling() {
        float low = attack(3.2F, 20, 20, 4, 3, 2.1F) / 3.2F;
        float high = attack(60.8F, 1214, 1214, 242.8F, 3, 2.1F) / 60.8F;
        assertEquals(low, high, EPS);
    }

    @Test
    void observedHealthLossIgnoresSamePriorityCapsAndCancellation() {
        assertEquals(0.0F, PostureBalance.actualHealthLoss(100.0F, 100.0F), EPS);
        assertEquals(20.0F, PostureBalance.actualHealthLoss(100.0F, 80.0F), EPS);
        assertEquals(0.0F, PostureBalance.actualHealthLoss(100.0F, 120.0F), EPS);
        assertEquals(0.0F, PostureBalance.actualHealthLoss(100.0F, Float.NaN), EPS);
    }

    @Test
    void overkillIsClippedAndZeroDamageIsIgnored() {
        assertEquals(attack(10, 100, 10, 10, 3, 2.1F),
                attack(10, 100, 10, 1000, 3, 2.1F), EPS);
        assertEquals(0.0F, attack(10, 100, 100, 0, 3, 2.1F), EPS);
        assertEquals(0.0F, attack(10, 100, 100, 50, 0, 2.1F), EPS);
        assertEquals(0.0F, attack(10, 0, 100, 50, 3, 2.1F), EPS);
        assertEquals(0.0F, attack(10, 100, 100, Float.NaN, 3, 2.1F), EPS);
        assertEquals(0.0F, attack(10, 100, 100, 50, 3, 0), EPS);
    }

    @Test
    void impactThreeFinisherBreaksNearTwoPercentRemainingHp() {
        // Coefficient 2.1 and Impact 3: continuous threshold at ~2.42% HP.
        assertTrue(2.1 * (.25 * .98 + .25 * Math.pow(.98, 3)) >= 1.0);
        assertTrue(2.1 * (.25 * .975 + .25 * Math.pow(.975, 3)) < 1.0);
    }

    @Test
    void impactOnePointEightNeedsParriesOrOtherPostureSources() {
        // Impact 1.8 no longer breaks posture from HP damage alone before death
        // (no regeneration) at the new coefficient 2.1.
        double efficiency = PostureBalance.impactEfficiency(1.8F);
        assertTrue(2.1 * efficiency * .5 < 1.0);
        assertTrue(2.1 * efficiency * .5 > .97);
    }

    @Test
    void additionalBreakResistanceScalesAndCaps() {
        assertEquals(1.0F, PostureBalance.breakResistanceMultiplier(0, 1.0F, 3.0F), EPS);
        assertEquals(2.0F, PostureBalance.breakResistanceMultiplier(1, 1.0F, 3.0F), EPS);
        assertEquals(3.0F, PostureBalance.breakResistanceMultiplier(2, 1.0F, 3.0F), EPS);
        assertEquals(3.0F, PostureBalance.breakResistanceMultiplier(3, 1.0F, 3.0F), EPS);
        assertEquals(3.0F, PostureBalance.breakResistanceMultiplier(
                Integer.MAX_VALUE, 1.0F, 3.0F), EPS);
        assertEquals(1.25F, PostureBalance.breakResistanceMultiplier(1, .25F, 2.0F), EPS);
        assertEquals(2.0F, PostureBalance.breakResistanceMultiplier(20, .25F, 2.0F), EPS);
        assertEquals(1.0F, PostureBalance.breakResistanceMultiplier(20, 0.0F, 3.0F), EPS);
        assertEquals(1.0F, PostureBalance.breakResistanceMultiplier(20, 1.0F, 1.0F), EPS);
    }

    @Test
    void resistanceMathHandlesInvalidTuningSafely() {
        assertEquals(1.0F, PostureBalance.breakResistanceMultiplier(
                -1, 1.0F, 3.0F), EPS);
        assertEquals(1.0F, PostureBalance.breakResistanceMultiplier(
                1, Float.NaN, 3.0F), EPS);
        assertEquals(1.0F, PostureBalance.breakResistanceMultiplier(
                1, 1.0F, Float.NaN), EPS);
    }

    @Test
    void configuredParryEndpointsMatchAtTwentyAndAtHighEhp() {
        assertEquals(.18F, PostureBalance.parryPostureFraction(
                .18F, .06F, 20, 20, .7F), EPS);
        assertEquals(.18F, PostureBalance.parryPostureFraction(
                .18F, .06F, 10, 20, .7F), EPS);
        assertEquals(.18F, PostureBalance.parryPostureFraction(
                .18F, .06F, 1, 20, .7F), EPS);
        assertEquals(.083943F, PostureBalance.parryPostureFraction(
                .18F, .06F, 200, 20, .7F), EPS);
        assertEquals(.06F, PostureBalance.parryPostureFraction(
                .18F, .06F, Float.MAX_VALUE, 20, .7F), EPS);
    }

    @Test
    void endpointsAreIndependentAndTheCurveKeepsItsExponent() {
        // Changing only the low-EHP anchor preserves the high-EHP asymptote.
        assertEquals(.24F, PostureBalance.parryPostureFraction(
                .24F, .06F, 20, 20, .7F), EPS);
        assertEquals(.06F, PostureBalance.parryPostureFraction(
                .24F, .06F, Float.MAX_VALUE, 20, .7F), EPS);
        // Independent high-EHP tuning, keeping the same 18% low-EHP anchor.
        assertEquals(.18F, PostureBalance.parryPostureFraction(
                .18F, .03F, 20, 20, .7F), EPS);
        assertEquals(.03F, PostureBalance.parryPostureFraction(
                .18F, .03F, Float.MAX_VALUE, 20, .7F), EPS);
        assertEquals(.06F + .12F * (float) Math.pow(.2D, .7D),
                PostureBalance.parryPostureFraction(
                        .18F, .06F, 100, 20, .7F), EPS);
    }

    @Test
    void parryConfigurationInputIsClampedToFractions() {
        assertEquals(1.0F, PostureBalance.parryPostureFraction(
                2.0F, -.5F, 10, 20, .7F), EPS);
        assertEquals(0.0F, PostureBalance.parryPostureFraction(
                0.0F, 0.0F, 200, 20, .7F), EPS);
        assertEquals(0.0F, PostureBalance.parryDamage(
                0.0F, .18F, .06F, 20, 20, .7F), EPS);
        assertEquals(1.8F, PostureBalance.parryDamage(
                10.0F, .18F, .06F, 20, 20, .7F), EPS);
    }

    @Test
    void parryFinisherStillPenalizesInnatelyDurableBosses() {
        float zombie = PostureBalance.parryPostureFraction(.18F, .06F, 20, 20, .7F)
                * PostureBalance.healthPostureDamageMultiplier(
                        1, .25F, 1, 2, 20, 40, 200);
        float boss = PostureBalance.parryPostureFraction(.18F, .06F, 200, 20, .7F)
                * PostureBalance.healthPostureDamageMultiplier(
                        1, .25F, 1, 2, 200, 40, 200);
        assertEquals(.18F, zombie, EPS);
        assertEquals(.25F * PostureBalance.parryPostureFraction(
                .18F, .06F, 200, 20, .7F), boss, EPS);
        assertTrue(boss < .022F);
        assertTrue(zombie > boss * 8);
        assertEquals(.25F, PostureBalance.healthPostureDamageMultiplier(
                1, .25F, 1, 2, 500, 40, 200), EPS);
        assertEquals(1.0F, PostureBalance.healthPostureDamageMultiplier(
                1, .25F, 1, 2, 20, 40, 200), EPS);
        assertEquals(.5F, PostureBalance.healthScalingStrength(120, 40, 200), EPS);
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
