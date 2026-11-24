package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRangePolicyTest {
    private static final double EPS = 1.0E-9;

    /** Epic Fight ColliderPreset values: half size (x, y, z), then center (x, y, z). */
    private static double reach(double hx, double hy, double hz, double cx, double cy, double cz) {
        return ExecutionRangePolicy.colliderReach(cx, cy, cz, hx, hy, hz);
    }

    private static final double SWORD = reach(0.4, 0.4, 0.7, 0, 0, -0.35);
    private static final double DAGGER = reach(0.4, 0.4, 0.6, 0, 0, -0.1);
    private static final double LONGSWORD = reach(0.4, 0.4, 0.8, 0, 0, -0.75);
    private static final double TACHI = reach(0.4, 0.4, 0.95, 0, 0, -0.95);
    private static final double SPEAR = reach(0.6, 0.6, 1.0, 0, 0, -1.0);
    private static final double GREATSWORD = reach(0.5, 0.8, 1.0, 0, 0, -1.0);
    private static final double FIST = reach(0.4, 0.4, 0.4, 0, 0, 0);

    @Test
    void swordKeepsCombatEvolutionRangesExactly() {
        assertEquals(4.0, ExecutionRangePolicy.range(4.0, 1.0, SWORD), EPS);
        assertEquals(2.0, ExecutionRangePolicy.range(2.0, 1.0, SWORD), EPS);
    }

    @Test
    void longerWeaponsReachFartherAndShorterOnesLess() {
        double dagger = ExecutionRangePolicy.range(4.0, 1.0, DAGGER);
        double longsword = ExecutionRangePolicy.range(4.0, 1.0, LONGSWORD);
        double tachi = ExecutionRangePolicy.range(4.0, 1.0, TACHI);
        assertTrue(dagger < 4.0 && dagger >= 3.0, "dagger " + dagger);
        assertTrue(longsword > 4.0 && longsword < tachi, "longsword " + longsword);
        assertTrue(tachi <= 6.0, "tachi " + tachi);
    }

    @Test
    void weaponFactorIsBounded() {
        assertEquals(6.0, ExecutionRangePolicy.range(4.0, 1.0, SPEAR), EPS);
        assertEquals(6.0, ExecutionRangePolicy.range(4.0, 1.0, GREATSWORD), EPS);
        assertEquals(3.0, ExecutionRangePolicy.range(2.0, 1.0, 100.0), EPS);
        assertEquals(3.0, ExecutionRangePolicy.range(4.0, 1.0, FIST), EPS);
        assertEquals(1.5, ExecutionRangePolicy.range(2.0, 1.0, FIST), EPS);
    }

    @Test
    void multiplierScalesLinearlyAndZeroDisables() {
        assertEquals(8.0, ExecutionRangePolicy.range(4.0, 2.0, SWORD), EPS);
        assertEquals(2.0, ExecutionRangePolicy.range(4.0, 0.5, SWORD), EPS);
        assertEquals(4.0, ExecutionRangePolicy.range(4.0, 0.0, SPEAR), EPS, "0.0 = Combat Evolution default");
        assertEquals(4.0, ExecutionRangePolicy.range(4.0, Double.NaN, SPEAR), EPS);
        assertEquals(4.0, ExecutionRangePolicy.range(4.0, -1.0, SPEAR), EPS);
        assertFalse(ExecutionRangePolicy.isEnabled(0.0));
        assertEquals(16.0, ExecutionRangePolicy.range(4.0, 100.0, SWORD), EPS, "multiplier capped at 4");
    }

    @Test
    void unknownWeaponsUseCombatEvolutionRange() {
        assertEquals(4.0, ExecutionRangePolicy.range(4.0, 1.0, Double.NaN), EPS);
        assertEquals(2.0, ExecutionRangePolicy.range(2.0, 1.0, 0.0), EPS);
    }

    @Test
    void largeMobsGetASurfaceAllowance() {
        assertEquals(0.0, ExecutionRangePolicy.sizeBonus(0.6, 1.0), EPS, "player-sized mobs unchanged");
        assertEquals(0.675, ExecutionRangePolicy.sizeBonus(1.95, 1.0), EPS, "ravager-sized");
        assertEquals(3.0, ExecutionRangePolicy.sizeBonus(20.0, 1.0), EPS, "capped");
        assertEquals(0.0, ExecutionRangePolicy.sizeBonus(1.95, 0.0), EPS, "disabled feature adds nothing");
        assertEquals(0.0, ExecutionRangePolicy.sizeBonus(Double.NaN, 1.0), EPS);
    }
}
