package dev.nire9734.epicfightposture;

/** Pure balance math shared by the server posture runtime and unit tests. */
final class PostureBalance {
    private static final float DEFAULT_PARRY_EHP_EXPONENT = 0.7F;
    private static final float DEFAULT_EHP_REFERENCE = 20.0F;

    /**
     * Two anchored parry fractions: maxBaseFraction at innate EHP <= reference
     * (20), and minBaseFraction as EHP tends to infinity. Innate EHP below 20
     * is clamped, so parry pressure cannot exceed the configured maximum base endpoint
     * solely because a mob has less than 20 innate EHP.
     *
     * The fraction is independent of the target's current HP.
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
     * Final observed HP loss drives direct-hit pressure linearly. The internal
     * default coefficient is 0.54: at Impact 3, removing the entire HP bar
     * removes 54% of unmultiplied base posture, before break resistance.
     * A larger maximum-posture multiplier makes attacks relatively weaker.
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
        double posture = baseMaxPosture * coefficient * impactScale * (before - after);
        return Double.isFinite(posture) && posture > 0.0D
                ? (float) Math.min(posture, 1_000_000_000.0D) : 0.0F;
    }

    /** Prefer an EntityType's registered baseline over a mutable instance base. */
    static float innateAttributeValue(float typeDefault, float instanceBase, float fallback) {
        if (Float.isFinite(typeDefault)) {
            return typeDefault;
        }
        if (Float.isFinite(instanceBase)) {
            return instanceBase;
        }
        return fallback;
    }

    /** Reference-hit EHP shared by runtime posture calculations and unit tests. */
    static float effectiveHealth(float health, float armor, float toughness, float referenceDamage) {
        float divisor = Math.max(0.01F, 2.0F + toughness / 4.0F);
        float armorPoints = clamp(armor - referenceDamage / divisor, armor * 0.2F, 20.0F);
        float receivedFraction = clamp(1.0F - armorPoints / 25.0F, 0.2F, 1.0F);
        return health / Math.max(receivedFraction, 1.0E-4F);
    }

    /**
     * Required posture-damage multiplier after successful breaks.
     * New defaults: first 1x, second 1.5x, third and later 2x.
     * Called only on attack/parry, never every tick.
     */
    static float breakResistanceMultiplier(int completedBreaks, float step, float maximum) {
        if (completedBreaks <= 0) {
            return 1.0F;
        }
        float safeStep = Float.isFinite(step) ? Math.max(0.0F, step) : 0.0F;
        float safeMaximum = Float.isFinite(maximum) ? Math.max(1.0F, maximum) : 1.0F;
        double increased = 1.0D + (double) completedBreaks * safeStep;
        return (float) Math.min((double) safeMaximum, increased);
    }

    /** Return the actual remaining posture; invalid or ineffective hits cannot restart regeneration. */
    static float reducedPosture(float current, float damage, float resistance) {
        if (!Float.isFinite(current) || current <= 0.0F
                || !Float.isFinite(damage) || damage <= 0.0F
                || !Float.isFinite(resistance) || resistance <= 0.0F) {
            return current;
        }
        return Math.max(0.0F, current - damage / resistance);
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

    private static float sanitizePositive(float value, float fallback) {
        return Float.isFinite(value) && value > 0.0F ? value : fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private PostureBalance() {
    }
}
