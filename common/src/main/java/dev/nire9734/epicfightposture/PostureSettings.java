package dev.nire9734.epicfightposture;

/** Server Posture settings shared by both loaders, already converted to fractions and ticks. */
public record PostureSettings(
        float parryBaseMaxFraction,
        float parryBaseMinFraction,
        float breakResistanceStep,
        float breakResistanceMax,
        int regenDelayTicks,
        float regenFractionPerSecond,
        boolean guardPostureEnabled,
        float guardPostureMultiplier,
        float projectileDefenseMultiplier) {
}
