package dev.nire9734.epicfightposture;

/** Pure balance math shared by the server posture runtime and unit tests. */
final class PostureBalance {
    private static final float DEFAULT_PARRY_EHP_EXPONENT = 0.7F;
    private static final float DEFAULT_EHP_REFERENCE = 20.0F;
    private static final float DEFAULT_HEALTH_SCALING_EHP_START = 40.0F;
    private static final float DEFAULT_HEALTH_SCALING_EHP_END = 200.0F;

    /**
     * Two anchored parry fractions: maxBaseFraction at innate EHP <= reference
     * (20), and minBaseFraction as EHP tends to infinity. Innate EHP below 20
     * is clamped, so parry pressure cannot exceed the configured maximum base endpoint
     * solely because a mob has less than 20 innate EHP.
     *
     * The HP-dependent finisher modifier is applied separately by the caller.
     */
    static float parryPostureFraction(
            float maxBaseFraction,
            float minBaseFraction,
            float effectiveHealth,
            float referenceEffectiveHealth,
            float scalingExponent) {
        float maxBase = sanitizeFraction(maxBaseFraction);
        float minBase = sanitizeFraction(minBaseFraction);
        float reference = sanitizePositive(referenceEffectiveHealth, DEFAULT_EHP_REFERENCE);
        float ehp = sanitizePositive(effectiveHealth, reference);
        float exponent = Float.isFinite(scalingExponent)
                ? clamp(scalingExponent, 0.0F, 4.0F)
                : DEFAULT_PARRY_EHP_EXPONENT;
        double referenceRatio = Math.min(1.0D, (double) reference / Math.max((double) ehp, reference));
        double blend = Math.pow(referenceRatio, exponent);
        double fraction = minBase + (maxBase - minBase) * blend;
        return Double.isFinite(fraction)
                ? (float) clamp((float) fraction, 0.0F, 1.0F)
                : maxBase;
    }

    static float parryDamage(
            float maxPosture,
            float maxBaseFraction,
            float minBaseFraction,
            float effectiveHealth,
            float referenceEffectiveHealth,
            float scalingExponent) {
        if (!Float.isFinite(maxPosture) || maxPosture <= 0.0F) {
            return 0.0F;
        }
        return maxPosture * parryPostureFraction(
                maxBaseFraction,
                minBaseFraction,
                effectiveHealth,
                referenceEffectiveHealth,
                scalingExponent
        );
    }

    /**
     * Observe only real positive HP reductions; a canceled or fully capped
     * damage event may have had a positive amount before the final listeners.
     */
    static float actualHealthLoss(float before, float after) {
        if (!Float.isFinite(before) || !Float.isFinite(after)) {
            return 0.0F;
        }
        return Math.max(0.0F, before - after);
    }

    /** Bounded, secondary weapon-weight bonus for nonzero Epic Fight Impact. */
    static float impactEfficiency(float impact) {
        if (!Float.isFinite(impact) || impact <= 0.0F) {
            return 0.0F;
        }
        return clamp((float) (0.7D + 0.3D * Math.sqrt(impact / 3.0D)), 0.7F, 1.5F);
    }

    /**
     * Final post-mitigation HP damage drives posture. Integration of the health
     * finisher curve makes cumulative pressure independent of hit splitting.
     * baseMaxPosture excludes the global/per-type maximum posture multiplier.
     */
    static float attackPostureDamage(float baseMaxPosture, float maxHealth,
            float healthBefore, float finalDamage, float impact, float coefficient) {
        if (!Float.isFinite(baseMaxPosture) || baseMaxPosture <= 0.0F
                || !Float.isFinite(maxHealth) || maxHealth <= 0.0F
                || !Float.isFinite(healthBefore) || healthBefore <= 0.0F
                || !Float.isFinite(finalDamage) || finalDamage <= 0.0F
                || !Float.isFinite(coefficient) || coefficient <= 0.0F) {
            return 0.0F;
        }
        float impactScale = impactEfficiency(impact);
        if (impactScale <= 0.0F) {
            return 0.0F;
        }

        double before = clamp(healthBefore / maxHealth, 0.0F, 1.0F);
        double after = clamp((healthBefore - finalDamage) / maxHealth, 0.0F, 1.0F);
        if (after >= before) {
            return 0.0F;
        }
        double weighted = finisherIntegral(1.0D - after) - finisherIntegral(1.0D - before);
        double posture = baseMaxPosture * coefficient * impactScale * weighted;
        return Double.isFinite(posture) && posture > 0.0D
                ? (float) Math.min(posture, 1_000_000_000.0D) : 0.0F;
    }

    /** Integral of 0.25 + 0.75 * depletedHealth^2. */
    private static double finisherIntegral(double depletedHealth) {
        return 0.25D * depletedHealth + 0.25D * depletedHealth * depletedHealth * depletedHealth;
    }

    static float healthPostureDamageMultiplier(
            float healthFraction,
            float fullHealthMultiplier,
            float zeroHealthMultiplier,
            float curveExponent) {
        if (!Float.isFinite(healthFraction)) {
            healthFraction = 1.0F;
        }
        float health = clamp(healthFraction, 0.0F, 1.0F);
        float full = sanitizeNonNegative(fullHealthMultiplier, 1.0F);
        float zero = sanitizeNonNegative(zeroHealthMultiplier, full);
        float exponent = sanitizePositive(curveExponent, 1.0F);
        float depleted = 1.0F - health;
        float progress = (float) Math.pow(depleted, exponent);
        return full + (zero - full) * progress;
    }

    /**
     * Blends the health-based finisher curve in according to target effective health.
     * Low-EHP mobs use a neutral x1.0 multiplier; high-EHP enemies use the full configured curve.
     */
    static float healthPostureDamageMultiplier(
            float healthFraction,
            float fullHealthMultiplier,
            float zeroHealthMultiplier,
            float curveExponent,
            float effectiveHealth,
            float scalingEhpStart,
            float scalingEhpEnd) {
        float bossCurve = healthPostureDamageMultiplier(
                healthFraction,
                fullHealthMultiplier,
                zeroHealthMultiplier,
                curveExponent
        );
        float strength = healthScalingStrength(effectiveHealth, scalingEhpStart, scalingEhpEnd);
        return interpolate(1.0F, bossCurve, strength);
    }

    static float healthScalingStrength(float effectiveHealth, float scalingEhpStart, float scalingEhpEnd) {
        float ehp = Float.isFinite(effectiveHealth) ? Math.max(0.0F, effectiveHealth) : 0.0F;
        float start = Float.isFinite(scalingEhpStart)
                ? Math.max(0.0F, scalingEhpStart)
                : DEFAULT_HEALTH_SCALING_EHP_START;
        float end = Float.isFinite(scalingEhpEnd)
                ? Math.max(0.0F, scalingEhpEnd)
                : DEFAULT_HEALTH_SCALING_EHP_END;

        if (end < start) {
            float swap = start;
            start = end;
            end = swap;
        }
        if (Math.abs(end - start) <= 1.0E-4F) {
            return ehp >= end ? 1.0F : 0.0F;
        }

        float t = clamp((ehp - start) / (end - start), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    static int regenDelayTicks(float delaySeconds) {
        if (!Float.isFinite(delaySeconds) || delaySeconds <= 0.0F) {
            return 0;
        }
        return Math.max(0, Math.round(delaySeconds * 20.0F));
    }

    private static float sanitizeFraction(float value) {
        return Float.isFinite(value) ? clamp(value, 0.0F, 1.0F) : 0.0F;
    }

    private static float sanitizeNonNegative(float value, float fallback) {
        return Float.isFinite(value) ? Math.max(0.0F, value) : fallback;
    }

    private static float sanitizePositive(float value, float fallback) {
        return Float.isFinite(value) && value > 0.0F ? value : fallback;
    }

    private static float interpolate(float start, float end, float progress) {
        return start + (end - start) * clamp(progress, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private PostureBalance() {
    }
}
