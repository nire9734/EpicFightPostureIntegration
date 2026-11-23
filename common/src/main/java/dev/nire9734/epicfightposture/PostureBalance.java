package dev.nire9734.epicfightposture;

/** Pure balance math shared by the server posture runtime and unit tests. */
final class PostureBalance {
    private static final float DEFAULT_PARRY_EHP_EXPONENT = (float) PostureTuning.EHP_EXPONENT;
    private static final float DEFAULT_EHP_REFERENCE = PostureTuning.EHP_REFERENCE_HEALTH;

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
        // An inverted configuration (min > max) is treated as the same range
        // in the intended order, so weak mobs never take less parry pressure.
        float first = sanitizeFraction(maxBaseFraction);
        float second = sanitizeFraction(minBaseFraction);
        float maxBase = Math.max(first, second);
        float minBase = Math.min(first, second);
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

    /** Parry damage is a percentage of maximum posture, classified by innate rather than live EHP. */
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

    /** A paid normal guard inflicts this fraction of parry Posture damage. */
    static float guardDamage(float parryPressure, float multiplier) {
        if (!Float.isFinite(parryPressure) || parryPressure <= 0.0F
                || !Float.isFinite(multiplier) || multiplier <= 0.0F) {
            return 0.0F;
        }
        return (float) Math.min((double) parryPressure * multiplier, 1_000_000_000.0D);
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
     * default coefficient is 0.75: at Impact 3, removing the entire HP bar
     * removes 75% of unmultiplied base posture, before break resistance.
     * The attack multiplier is applied to the coefficient, without changing maximum posture.
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
     * Effective health against the reference hit, with non-positive or
     * non-finite inputs replaced by the reference mob values.
     */
    static float referenceEffectiveHealth(float maxHealth, float armor, float toughness) {
        float health = Float.isFinite(maxHealth) && maxHealth > 0.0F
                ? maxHealth : PostureTuning.EHP_REFERENCE_HEALTH;
        float safeArmor = Float.isFinite(armor) ? armor : 0.0F;
        float safeToughness = Float.isFinite(toughness) ? toughness : 0.0F;
        float value = effectiveHealth(health, safeArmor, safeToughness, PostureTuning.EHP_REFERENCE_DAMAGE);
        return Float.isFinite(value) && value > 0.0F ? value : PostureTuning.EHP_REFERENCE_HEALTH;
    }

    /** Sub-linear EHP Posture scale, bounded but not floored by the minimum Posture. */
    static float basePosture(float effectiveHealth) {
        float ehp = Float.isFinite(effectiveHealth) && effectiveHealth > 0.0F
                ? effectiveHealth : PostureTuning.EHP_REFERENCE_HEALTH;
        float ratio = Math.max(PostureTuning.MIN_EHP_RATIO, ehp / PostureTuning.EHP_REFERENCE_HEALTH);
        float posture = PostureTuning.EHP_REFERENCE_POSTURE
                * (float) Math.pow(ratio, PostureTuning.EHP_EXPONENT);
        if (!Float.isFinite(posture) || posture <= 0.0F) {
            posture = PostureTuning.EHP_REFERENCE_POSTURE;
        }
        return clamp(posture, PostureTuning.MIN_SAFE_POSTURE, PostureTuning.MAX_SAFE_POSTURE);
    }

    /** Maximum Posture: the EHP scale floored by the minimum Posture. */
    static float maximumPosture(float effectiveHealth, float minimum) {
        return clamp(Math.max(minimum, basePosture(effectiveHealth)),
                PostureTuning.MIN_SAFE_POSTURE, PostureTuning.MAX_SAFE_POSTURE);
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
