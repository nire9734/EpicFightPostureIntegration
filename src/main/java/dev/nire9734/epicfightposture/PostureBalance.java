package dev.nire9734.epicfightposture;

/** Pure balance math shared by the server posture runtime and unit tests. */
final class PostureBalance {
    private static final float PARRY_MIN_DAMAGE = 0.5F;
    private static final float PARRY_MAX_FRACTION = 0.10F;

    private static final float LOW_HEALTH_THRESHOLD = 0.25F;
    private static final float MID_HEALTH_THRESHOLD = 0.50F;
    private static final float HIGH_HEALTH_THRESHOLD = 0.75F;

    static float parryDamage(float maxPosture) {
        if (!Float.isFinite(maxPosture) || maxPosture <= 0.0F) {
            return PARRY_MIN_DAMAGE;
        }
        return Math.max(PARRY_MIN_DAMAGE, maxPosture * PARRY_MAX_FRACTION);
    }

    static int regenDelayTicks(float delaySeconds) {
        if (!Float.isFinite(delaySeconds) || delaySeconds <= 0.0F) {
            return 0;
        }
        return Math.max(0, Math.round(delaySeconds * 20.0F));
    }

    /**
     * Returns the fraction of maximum posture regenerated per second.
     * Rates are linearly interpolated through the 25%, 50%, 75%, and 100% HP anchors.
     * Below 25% HP the 25% anchor rate is retained.
     */
    static float regenFractionPerSecond(
            float healthFraction,
            float lowHealthRegen,
            float midHealthRegen,
            float highHealthRegen,
            float fullHealthRegen) {
        if (!Float.isFinite(healthFraction)) {
            healthFraction = 1.0F;
        }

        float low = sanitizeRate(lowHealthRegen);
        float mid = sanitizeRate(midHealthRegen);
        float high = sanitizeRate(highHealthRegen);
        float full = sanitizeRate(fullHealthRegen);
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

    private static float sanitizeRate(float value) {
        return Float.isFinite(value) ? clamp(value, 0.0F, 1.0F) : 0.0F;
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
