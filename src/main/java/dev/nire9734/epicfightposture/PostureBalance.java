package dev.nire9734.epicfightposture;

/** Pure balance math shared by the server posture runtime and unit tests. */
final class PostureBalance {
    private static final float PARRY_MIN_DAMAGE = 0.5F;
    private static final float LOW_HEALTH_THRESHOLD = 0.25F;
    private static final float MID_HEALTH_THRESHOLD = 0.50F;
    private static final float HIGH_HEALTH_THRESHOLD = 0.75F;
    private static final float DEFAULT_ATTACK_DAMAGE_REFERENCE = 8.0F;
    private static final float DEFAULT_ATTACK_DAMAGE_EXPONENT = 0.7F;
    private static final float DEFAULT_HEALTH_SCALING_EHP_START = 40.0F;
    private static final float DEFAULT_HEALTH_SCALING_EHP_END = 200.0F;
    private static final float MAX_ATTACK_DAMAGE_SCALE = 100.0F;

    static float parryDamage(float maxPosture, float maxPostureFraction) {
        float fraction = sanitizeFraction(maxPostureFraction);
        if (!Float.isFinite(maxPosture) || maxPosture <= 0.0F) {
            return PARRY_MIN_DAMAGE;
        }
        return Math.max(PARRY_MIN_DAMAGE, maxPosture * fraction);
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

    /** Returns the fraction of maximum posture regenerated per second at the current health. */
    static float regenFractionPerSecond(
            float healthFraction,
            float lowHealthRegen,
            float midHealthRegen,
            float highHealthRegen,
            float fullHealthRegen) {
        if (!Float.isFinite(healthFraction)) {
            healthFraction = 1.0F;
        }

        float low = sanitizeFraction(lowHealthRegen);
        float mid = sanitizeFraction(midHealthRegen);
        float high = sanitizeFraction(highHealthRegen);
        float full = sanitizeFraction(fullHealthRegen);
        float health = clamp(healthFraction, 0.0F, 1.0F);

        if (health <= LOW_HEALTH_THRESHOLD) {
            return low;
        }
        if (health <= MID_HEALTH_THRESHOLD) {
            return interpolate(
                    low,
                    mid,
                    (health - LOW_HEALTH_THRESHOLD) / (MID_HEALTH_THRESHOLD - LOW_HEALTH_THRESHOLD)
            );
        }
        if (health <= HIGH_HEALTH_THRESHOLD) {
            return interpolate(
                    mid,
                    high,
                    (health - MID_HEALTH_THRESHOLD) / (HIGH_HEALTH_THRESHOLD - MID_HEALTH_THRESHOLD)
            );
        }
        return interpolate(
                high,
                full,
                (health - HIGH_HEALTH_THRESHOLD) / (1.0F - HIGH_HEALTH_THRESHOLD)
        );
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
