package dev.nire9734.epicfightposture;

/** Pure balance math shared by the server posture runtime and unit tests. */
final class PostureBalance {
    private static final float PARRY_MIN_DAMAGE = 0.5F;

    static float parryDamage(float maxPosture, float maxPostureFraction) {
        float fraction = sanitizeFraction(maxPostureFraction);
        if (!Float.isFinite(maxPosture) || maxPosture <= 0.0F) {
            return PARRY_MIN_DAMAGE;
        }
        return Math.max(PARRY_MIN_DAMAGE, maxPosture * fraction);
    }

    static float healthPostureDamageMultiplier(float healthFraction, float maxBonusFraction) {
        if (!Float.isFinite(healthFraction)) {
            healthFraction = 1.0F;
        }
        float health = clamp(healthFraction, 0.0F, 1.0F);
        float bonus = Float.isFinite(maxBonusFraction) ? Math.max(0.0F, maxBonusFraction) : 0.0F;
        return 1.0F + (1.0F - health) * bonus;
    }

    static int regenDelayTicks(float delaySeconds) {
        if (!Float.isFinite(delaySeconds) || delaySeconds <= 0.0F) {
            return 0;
        }
        return Math.max(0, Math.round(delaySeconds * 20.0F));
    }

    static float regenFractionPerSecond(float configuredRate) {
        return sanitizeFraction(configuredRate);
    }

    private static float sanitizeFraction(float value) {
        return Float.isFinite(value) ? clamp(value, 0.0F, 1.0F) : 0.0F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private PostureBalance() {
    }
}
