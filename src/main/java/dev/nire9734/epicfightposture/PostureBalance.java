package dev.nire9734.epicfightposture;

/** Pure balance math shared by the server posture runtime and unit tests. */
final class PostureBalance {
    private static final float PARRY_MIN_DAMAGE = 0.5F;
    private static final float LOW_HEALTH_THRESHOLD = 0.25F;
    private static final float MID_HEALTH_THRESHOLD = 0.50F;
    private static final float HIGH_HEALTH_THRESHOLD = 0.75F;

    static float parryDamage(float maxPosture, float maxPostureFraction) {
        float fraction = sanitizeFraction(maxPostureFraction);
        if (!Float.isFinite(maxPosture) || maxPosture <= 0.0F) {
            return PARRY_MIN_DAMAGE;
        }
        return Math.max(PARRY_MIN_DAMAGE, maxPosture * fraction);
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
