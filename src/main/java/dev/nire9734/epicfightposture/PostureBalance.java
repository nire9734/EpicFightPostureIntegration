package dev.nire9734.epicfightposture;

/** Pure balance math shared by the server posture runtime and unit tests. */
final class PostureBalance {
    private static final float PARRY_MIN_DAMAGE = 0.5F;
    private static final float PARRY_MAX_FRACTION = 0.10F;

    private static final float LOW_HEALTH_THRESHOLD = 0.25F;
    private static final float MID_HEALTH_THRESHOLD = 0.50F;
    private static final float HIGH_HEALTH_THRESHOLD = 0.75F;

    private static final float LOW_HEALTH_REGEN = 0.04F;
    private static final float MID_HEALTH_REGEN = 0.08F;
    private static final float HIGH_HEALTH_REGEN = 0.12F;
    private static final float FULL_HEALTH_REGEN = 0.15F;

    static float parryDamage(float maxPosture) {
        if (!Float.isFinite(maxPosture) || maxPosture <= 0.0F) {
            return PARRY_MIN_DAMAGE;
        }
        return Math.max(PARRY_MIN_DAMAGE, maxPosture * PARRY_MAX_FRACTION);
    }

    /**
     * Returns the fraction of maximum posture regenerated per second.
     *
     * <p>The curve is continuous and linearly interpolated through the intended
     * Sekiro-style anchors: 25% HP = 4%, 50% = 8%, 75% = 12%, 100% = 15%.
     * Below 25% HP the rate stays at 4% per second.</p>
     */
    static float regenFractionPerSecond(float healthFraction) {
        if (!Float.isFinite(healthFraction)) {
            healthFraction = 1.0F;
        }
        float health = clamp(healthFraction, 0.0F, 1.0F);
        if (health <= LOW_HEALTH_THRESHOLD) {
            return LOW_HEALTH_REGEN;
        }
        if (health <= MID_HEALTH_THRESHOLD) {
            return interpolate(
                    LOW_HEALTH_REGEN,
                    MID_HEALTH_REGEN,
                    (health - LOW_HEALTH_THRESHOLD) / (MID_HEALTH_THRESHOLD - LOW_HEALTH_THRESHOLD)
            );
        }
        if (health <= HIGH_HEALTH_THRESHOLD) {
            return interpolate(
                    MID_HEALTH_REGEN,
                    HIGH_HEALTH_REGEN,
                    (health - MID_HEALTH_THRESHOLD) / (HIGH_HEALTH_THRESHOLD - MID_HEALTH_THRESHOLD)
            );
        }
        return interpolate(
                HIGH_HEALTH_REGEN,
                FULL_HEALTH_REGEN,
                (health - HIGH_HEALTH_THRESHOLD) / (1.0F - HIGH_HEALTH_THRESHOLD)
        );
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
