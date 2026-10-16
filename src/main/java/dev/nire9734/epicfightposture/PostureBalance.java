package dev.nire9734.epicfightposture;

/** Pure balance math shared by the server posture runtime and unit tests. */
final class PostureBalance {
    private static final float DEFAULT_ATTACK_DAMAGE_REFERENCE = 8.0F;
    private static final float DEFAULT_ATTACK_DAMAGE_EXPONENT = 0.7F;
    private static final float DEFAULT_EHP_REFERENCE = 20.0F;
    private static final float DEFAULT_HEALTH_SCALING_EHP_START = 40.0F;
    private static final float DEFAULT_HEALTH_SCALING_EHP_END = 200.0F;
    private static final float MIN_EHP_RATIO = 0.01F;
    private static final float MAX_ATTACK_DAMAGE_SCALE = 100.0F;

    static float parryPostureFraction(
            float baseMaxPostureFraction,
            float effectiveHealth,
            float referenceEffectiveHealth,
            float scalingExponent) {
        float baseFraction = sanitizeFraction(baseMaxPostureFraction);
        float reference = sanitizePositive(referenceEffectiveHealth, DEFAULT_EHP_REFERENCE);
        float ehp = sanitizePositive(effectiveHealth, reference);
        float exponent = Float.isFinite(scalingExponent)
                ? clamp(scalingExponent, 0.0F, 4.0F)
                : DEFAULT_ATTACK_DAMAGE_EXPONENT;

        float ehpRatio = Math.max(MIN_EHP_RATIO, ehp / reference);
        double lowEhpBonus = Math.pow(1.0D / ehpRatio, exponent);
        double scaledFraction = baseFraction * (1.0D + lowEhpBonus);
        if (!Double.isFinite(scaledFraction)) {
            return baseFraction;
        }
        return (float) Math.max(0.0D, scaledFraction);
    }

    static float parryDamage(
            float maxPosture,
            float baseMaxPostureFraction,
            float effectiveHealth,
            float referenceEffectiveHealth,
            float scalingExponent) {
        if (!Float.isFinite(maxPosture) || maxPosture <= 0.0F) {
            return 0.0F;
        }
        return maxPosture * parryPostureFraction(
                baseMaxPostureFraction,
                effectiveHealth,
                referenceEffectiveHealth,
                scalingExponent
        );
    }

    static float attackDamageScale(float damageAmount, float referenceDamage, float exponent) {
        if (!Float.isFinite(damageAmount) || damageAmount <= 0.0F) {
            return 0.0F;
        }
        float reference = sanitizePositive(referenceDamage, DEFAULT_ATTACK_DAMAGE_REFERENCE);
        float scalingExponent = Float.isFinite(exponent)
                ? clamp(exponent, 0.0F, 4.0F)
                : DEFAULT_ATTACK_DAMAGE_EXPONENT;
        double scaled = Math.pow(damageAmount / reference, scalingExponent);
        if (!Double.isFinite(scaled)) {
            return MAX_ATTACK_DAMAGE_SCALE;
        }
        return clamp((float) scaled, 0.0F, MAX_ATTACK_DAMAGE_SCALE);
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
