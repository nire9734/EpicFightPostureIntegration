package dev.nire9734.epicfightposture.compat.humanoid;

/**
 * Platform-independent eligibility for filling one missing Epic Fight
 * humanoid neutralize animation. Does not make runtime-execution promises.
 */
public final class HumanoidNeutralizeFallbackPolicy {
    public static boolean shouldInstall(
            boolean humanoidArmature,
            boolean existingNeutralize,
            boolean ordinaryStunAnimation,
            boolean blacklisted) {
        return humanoidArmature
                && !existingNeutralize
                && ordinaryStunAnimation
                && !blacklisted;
    }

    private HumanoidNeutralizeFallbackPolicy() {}
}
