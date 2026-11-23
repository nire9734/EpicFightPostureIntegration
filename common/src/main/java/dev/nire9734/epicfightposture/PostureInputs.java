package dev.nire9734.epicfightposture;

/**
 * Sanitized live attribute inputs for maximum Posture. Each loader reads the
 * raw values from its own Minecraft attribute API and passes them through
 * {@link #of}, so both loaders derive Posture from identical numbers.
 *
 * Record equality compares float components bitwise (Float.compare), which
 * is what change detection between refreshes requires.
 */
record PostureInputs(float maxHealth, float armor, float toughness, float minimum, boolean useArmor) {
    static PostureInputs of(float rawMaxHealth, float rawArmor, float rawToughness,
                            float minimum, boolean useArmor) {
        return new PostureInputs(
                finiteOr(rawMaxHealth, PostureTuning.EHP_REFERENCE_HEALTH),
                useArmor ? finiteOr(rawArmor, 0.0F) : 0.0F,
                useArmor ? finiteOr(rawToughness, 0.0F) : 0.0F,
                finiteOr(minimum, PostureTuning.MINIMUM_POSTURE),
                useArmor);
    }

    /** Live effective health against the reference hit. */
    float effectiveHealth() {
        return PostureBalance.referenceEffectiveHealth(maxHealth, armor, toughness);
    }

    /** Unfloored Posture scale used to normalize direct-hit pressure. */
    float basePosture() {
        return PostureBalance.basePosture(effectiveHealth());
    }

    float maximumPosture() {
        return PostureBalance.maximumPosture(effectiveHealth(), minimum);
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
