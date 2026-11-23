package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the shared maximum-Posture math to the 3.2.2 Forge and NeoForge formulas. */
class PostureInputsTest {
    private static final float[][] SAMPLES = {
            {20, 0, 0}, {20, 2, 0}, {40, 10, 2}, {100, 20, 8}, {300, 30, 12},
            {1, 0, 0}, {0.5F, 0, 0}, {0, 0, 0}, {-5, 3, 0}, {1.0E9F, 30, 20},
            {Float.NaN, 0, 0}, {20, Float.NaN, Float.POSITIVE_INFINITY}, {500, 100, 0},
    };

    @Test
    void maximumPostureMatchesLegacyForgeFormula() {
        for (float[] sample : SAMPLES) {
            PostureInputs inputs = PostureInputs.of(sample[0], sample[1], sample[2], 1.0F, true);
            assertEquals(legacyForgeMax(sample[0], sample[1], sample[2], 1.0F), inputs.maximumPosture(), 0.0F,
                    () -> "max for " + java.util.Arrays.toString(sample));
        }
    }

    @Test
    void maximumPostureMatchesLegacyNeoForgeFormula() {
        for (float[] sample : SAMPLES) {
            PostureInputs inputs = PostureInputs.of(sample[0], sample[1], sample[2],
                    PostureTuning.MINIMUM_POSTURE, true);
            assertEquals(legacyNeoForgeMax(sample[0], sample[1], sample[2]), inputs.maximumPosture(), 0.0F,
                    () -> "NeoForge max for " + java.util.Arrays.toString(sample));
        }
    }

    @Test
    void basePostureMatchesLegacyAttackNormalization() {
        for (float[] sample : SAMPLES) {
            PostureInputs inputs = PostureInputs.of(sample[0], sample[1], sample[2], 1.0F, true);
            assertEquals(legacyBase(sample[0], sample[1], sample[2]), inputs.basePosture(), 0.0F,
                    () -> "base for " + java.util.Arrays.toString(sample));
        }
    }

    @Test
    void armorIsIgnoredWithoutArmorScaling() {
        assertEquals(PostureInputs.of(20, 0, 0, 1, false), PostureInputs.of(20, 15, 4, 1, false));
        assertTrue(PostureInputs.of(20, 15, 4, 1, true).maximumPosture()
                > PostureInputs.of(20, 15, 4, 1, false).maximumPosture());
    }

    @Test
    void refreshSkipsUnchangedInputsAndAppliesChangedOnes() {
        PostureInputs initial = PostureInputs.of(20, 0, 0, 1, true);
        PostureRuntimeState state = new PostureRuntimeState(initial);
        assertEquals(initial.maximumPosture(), state.max, 0.0F);
        assertSame(initial, state.lastInputs);

        state.current = state.max / 2.0F;
        PostureInputs same = PostureInputs.of(20, 0, 0, 1, true);
        state.refreshMaximum(same);
        assertSame(initial, state.lastInputs, "equal inputs must not be re-evaluated");

        PostureInputs stronger = PostureInputs.of(80, 0, 0, 1, true);
        state.refreshMaximum(stronger);
        assertNotSame(initial, state.lastInputs);
        assertEquals(stronger.maximumPosture(), state.max, 0.0F);
        assertEquals(state.max / 2.0F, state.current, 1.0E-5F, "posture ratio is preserved");
    }

    @Test
    void refreshIgnoresNegligibleMaximumChanges() {
        PostureRuntimeState state = new PostureRuntimeState(PostureInputs.of(20, 0, 0, 1, true));
        float before = state.max;
        state.refreshMaximum(PostureInputs.of(20.001F, 0, 0, 1, true));
        assertEquals(before, state.max, 0.0F);
    }

    @Test
    void refreshKeepsLockedPostureEmpty() {
        PostureRuntimeState state = new PostureRuntimeState(PostureInputs.of(20, 0, 0, 1, true));
        state.prepareBreak(0);
        state.confirmBreak();
        state.refreshMaximum(PostureInputs.of(200, 0, 0, 1, true));
        assertEquals(0.0F, state.current, 0.0F);
    }

    @Test
    void invertedParryRangeIsUsedInIntendedOrder() {
        for (float ehp : new float[] {5, 20, 60, 400, 1.0E6F}) {
            assertEquals(PostureBalance.parryPostureFraction(.22F, .10F, ehp, 20, .7F),
                    PostureBalance.parryPostureFraction(.10F, .22F, ehp, 20, .7F), 0.0F);
        }
        assertTrue(PostureConfigPolicy.isParryRangeInverted(10.0D, 22.0D));
        assertFalse(PostureConfigPolicy.isParryRangeInverted(22.0D, 10.0D));
        assertFalse(PostureConfigPolicy.isParryRangeInverted(15.0D, 15.0D));
        assertFalse(PostureConfigPolicy.isParryRangeInverted(Double.NaN, 10.0D));
    }

    // ---- 3.2.2 formulas, copied verbatim for regression comparison ----

    private static float legacyForgeMax(float rawMaxHealth, float rawArmor, float rawToughness, float minimum) {
        float maxHealth = finiteOr(rawMaxHealth, 20.0F);
        float armor = finiteOr(rawArmor, 0.0F);
        float toughness = finiteOr(rawToughness, 0.0F);
        if (maxHealth <= 0.0F) maxHealth = 20.0F;
        float effectiveHealth = PostureBalance.effectiveHealth(maxHealth, armor, toughness, 8.0F);
        if (!Float.isFinite(effectiveHealth) || effectiveHealth <= 0.0F) effectiveHealth = 20.0F;
        float ratio = Math.max(0.01F, effectiveHealth / 20.0F);
        float posture = 3.2F * (float) Math.pow(ratio, 0.7D);
        if (!Float.isFinite(posture) || posture <= 0.0F) posture = 3.2F;
        return clamp(Math.max(minimum, posture), 0.1F, 1_000_000.0F);
    }

    private static float legacyNeoForgeMax(float rawMaxHealth, float rawArmor, float rawToughness) {
        return Math.max(1.0F, legacyBase(rawMaxHealth, rawArmor, rawToughness));
    }

    private static float legacyBase(float rawMaxHealth, float rawArmor, float rawToughness) {
        float maxHealth = finiteOr(rawMaxHealth, 20.0F);
        float armor = finiteOr(rawArmor, 0.0F);
        float toughness = finiteOr(rawToughness, 0.0F);
        float ehp = PostureBalance.effectiveHealth(maxHealth <= 0.0F ? 20.0F : maxHealth, armor, toughness, 8.0F);
        ehp = Float.isFinite(ehp) && ehp > 0.0F ? ehp : 20.0F;
        float ratio = Math.max(.01F, ehp / 20.0F);
        return Math.max(.1F, Math.min(1_000_000.0F, 3.2F * (float) Math.pow(ratio, .7D)));
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
