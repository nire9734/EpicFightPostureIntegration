package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostureBalanceTest {
    private static final float EPS = 0.0001F;
    private static final float ATTACK_COEFFICIENT = 0.75F;

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
    void fullHealthBarProducesSeventyFivePercentPostureAtImpactThree() {
        assertEquals(7.5F, attack(10, 100, 100, 100, 3), EPS);
        assertEquals(3.75F, attack(10, 100, 100, 50, 3), EPS);
        assertEquals(3.75F, attack(10, 100, 50, 50, 3), EPS);
        assertTrue(attack(10, 100, 100, 100, 3) < 10.0F);
    }

    @Test
    void identicalHpLossHasNoCurrentHpFinisherBonus() {
        assertEquals(attack(10, 100, 100, 10, 3),
                attack(10, 100, 30, 10, 3), EPS);
        assertEquals(attack(10, 100, 100, 10, 3),
                attack(10, 100, 10, 10, 3), EPS);
    }

    @Test
    void splitHitsAreEquivalentToOneLargeHit() {
        float one = attack(20, 100, 100, 80, 3);
        float parts = attack(20, 100, 100, 30, 3)
                + attack(20, 100, 70, 25, 3)
                + attack(20, 100, 45, 25, 3);
        assertEquals(one, parts, EPS);
    }

    @Test
    void levelScaledHpRetainsNormalizedAttackPosture() {
        float zombie = attack(3.2F, 20, 20, 4, 3) / 3.2F;
        float leveled = attack(60.8F, 1214, 1214, 242.8F, 3) / 60.8F;
        assertEquals(.15F, zombie, EPS);
        assertEquals(zombie, leveled, EPS);
        assertEquals(.375F, attack(10, 100, 100, 50, 3) / 10, EPS);
    }

    @Test
    void independentAttackAndParryMultipliersDoNotAlterMaximumPosture() {
        float maximumPosture = 100.0F;
        float attackBase = attack(maximumPosture, 100, 100, 50, 3);
        float parryBase = PostureBalance.parryDamage(maximumPosture, .22F, .08F, 20, 20, .7F);

        assertEquals(37.5F, attackBase, EPS);
        assertEquals(22.0F, parryBase, EPS);
        assertEquals(75.0F, attackBase * 2.0F, EPS);
        assertEquals(11.0F, parryBase * 0.5F, EPS);
        assertEquals(100.0F, maximumPosture, EPS);
    }

    @Test
    void realObservedHpLossAndOverkillAreSafe() {
        assertEquals(0.0F, PostureBalance.actualHealthLoss(100, 100), EPS);
        assertEquals(20.0F, PostureBalance.actualHealthLoss(100, 80), EPS);
        assertEquals(0.0F, PostureBalance.actualHealthLoss(100, 120), EPS);
        assertEquals(0.0F, PostureBalance.actualHealthLoss(100, Float.NaN), EPS);
        assertEquals(attack(10, 100, 10, 10, 3), attack(10, 100, 10, 1000, 3), EPS);
        assertEquals(0.0F, attack(10, 100, 100, 0, 3), EPS);
        assertEquals(0.0F, attack(10, 100, 100, 50, 0), EPS);
        assertEquals(0.0F, attack(10, 0, 100, 50, 3), EPS);
        assertEquals(0.0F, attack(10, 100, 100, Float.NaN, 3), EPS);
        assertEquals(0.0F, PostureBalance.attackPostureDamage(10, 100, 100, 50, 3, 0), EPS);
    }

    @Test
    void innateSupplierDefaultsTakePriorityOverMutatedInstanceBases() {
        assertEquals(20.0F, PostureBalance.innateAttributeValue(20, 2000, 20), EPS);
        assertEquals(420.0F, PostureBalance.innateAttributeValue(420, 38152.8F, 20), EPS);
        assertEquals(10.0F, PostureBalance.innateAttributeValue(10, 37.38F, 0), EPS);
        assertEquals(0.0F, PostureBalance.innateAttributeValue(0, 100, 4), EPS);
        assertEquals(150.0F, PostureBalance.innateAttributeValue(Float.NaN, 150, 20), EPS);
        assertEquals(20.0F, PostureBalance.innateAttributeValue(Float.NaN, Float.NaN, 20), EPS);
        assertEquals(20.0F, PostureBalance.innateAttributeValue(Float.POSITIVE_INFINITY, 20, 5), EPS);
    }

    @Test
    void defaultEhpAndParryFractionsRemainStableAtHighLevels() {
        float innateZombieEhp = PostureBalance.effectiveHealth(
                PostureBalance.innateAttributeValue(20, 10000, 20), 2, 0, 8);
        float innateBossEhp = PostureBalance.effectiveHealth(
                PostureBalance.innateAttributeValue(420, 38152.8007F, 20),
                PostureBalance.innateAttributeValue(10, 37.3899F, 0), 0, 8);
        float leveledBossEhp = PostureBalance.effectiveHealth(38152.8007F, 37.3899F, 0, 8);
        assertEquals(20.325203F, innateZombieEhp, EPS);
        assertEquals(552.6316F, innateBossEhp, EPS);
        assertEquals(190764.0F, leveledBossEhp, 0.1F);
        float zombieParry = parryFraction(innateZombieEhp);
        float bossParry = parryFraction(innateBossEhp);
        assertEquals(.218428F, zombieParry, EPS);
        assertEquals(.093713F, bossParry, EPS);
        assertEquals(bossParry * 100.0F, PostureBalance.parryDamage(
                100, .22F, .08F, innateBossEhp, 20, .7F), EPS);
        // Break thresholds with no regeneration/attack pressure:
        assertEquals(11, (int) Math.ceil(1.0D / bossParry));
        assertEquals(17, (int) Math.ceil(1.5D / bossParry));
        assertEquals(5, (int) Math.ceil(1.0D / zombieParry));
        assertEquals(7, (int) Math.ceil(1.5D / zombieParry));
    }

    @Test
    void parryEndpointsAndExponentRemainUnchanged() {
        assertEquals(.22F, parryFraction(20), EPS);
        assertEquals(.22F, parryFraction(10), EPS);
        assertEquals(.107934F, parryFraction(200), EPS);
        assertEquals(.08F, parryFraction(Float.MAX_VALUE), EPS);
        assertEquals(.24F, PostureBalance.parryPostureFraction(.24F, .06F, 20, 20, .7F), EPS);
        assertEquals(.03F, PostureBalance.parryPostureFraction(.18F, .03F, Float.MAX_VALUE, 20, .7F), EPS);
        assertEquals(1.0F, PostureBalance.parryPostureFraction(2, -.5F, 10, 20, .7F), EPS);
        assertEquals(0.0F, PostureBalance.parryDamage(0, .18F, .06F, 20, 20, .7F), EPS);
    }

    @Test
    void resistanceDefaultsScaleHalfStepAndCapAtTwo() {
        assertEquals(1.0F, PostureBalance.breakResistanceMultiplier(0, .5F, 2), EPS);
        assertEquals(1.5F, PostureBalance.breakResistanceMultiplier(1, .5F, 2), EPS);
        assertEquals(2.0F, PostureBalance.breakResistanceMultiplier(2, .5F, 2), EPS);
        assertEquals(2.0F, PostureBalance.breakResistanceMultiplier(20, .5F, 2), EPS);
        assertEquals(2.0F, PostureBalance.breakResistanceMultiplier(Integer.MAX_VALUE, .5F, 2), EPS);
        assertEquals(1.0F, PostureBalance.breakResistanceMultiplier(-1, 1, 3), EPS);
        assertEquals(1.0F, PostureBalance.breakResistanceMultiplier(1, Float.NaN, 3), EPS);
        assertEquals(1.0F, PostureBalance.breakResistanceMultiplier(1, 1, Float.NaN), EPS);
    }

    @Test
    void regenerationDelayDefaultsToFifteenSeconds() {
        assertEquals(300, PostureBalance.regenDelayTicks(15.0F));
        assertEquals(200, PostureBalance.regenDelayTicks(10.0F));
        assertEquals(0, PostureBalance.regenDelayTicks(0.0F));
    }

    @Test
    void onlyActualPostureReductionsCanRestartRegeneration() {
        assertEquals(75.0F, PostureBalance.reducedPosture(100, 50, 2), EPS);
        assertEquals(0.0F, PostureBalance.reducedPosture(10, 50, 2), EPS);
        assertEquals(75.0F, PostureBalance.reducedPosture(75, 0, 1), EPS);
        assertEquals(75.0F, PostureBalance.reducedPosture(75, -10, 1), EPS);
        assertEquals(75.0F, PostureBalance.reducedPosture(75, Float.NaN, 1), EPS);
        assertEquals(75.0F, PostureBalance.reducedPosture(75, 10, 0), EPS);
        assertEquals(75.0F, PostureBalance.reducedPosture(75, 10, Float.NaN), EPS);
        assertEquals(0.0F, PostureBalance.reducedPosture(0, 10, 1), EPS);
        assertEquals(1_000_000.0F,
                PostureBalance.reducedPosture(1_000_000.0F, Float.MIN_VALUE, 1), EPS);
    }

    private static float parryFraction(float ehp) {
        return PostureBalance.parryPostureFraction(.22F, .08F, ehp, 20, .7F);
    }

    private static float attack(float basePosture, float maxHp, float hpBefore,
                                float hpDamage, float impact) {
        return PostureBalance.attackPostureDamage(
                basePosture, maxHp, hpBefore, hpDamage, impact, ATTACK_COEFFICIENT);
    }
}
