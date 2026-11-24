package dev.nire9734.epicfightposture;

/** Pure normal-guard eligibility shared by both Epic Fight loader adapters. */
public final class GuardPosturePolicy {
    /**
     * A paid normal guard applies pressure unless it was a parry. With Guard,
     * weapons that can perform the standard parry are excluded (they should
     * parry instead). Impact Guard cannot parry at all, so it is never excluded
     * on that basis.
     */
    public static boolean shouldApply(boolean paidNormalGuard, boolean parried,
                                      boolean weaponSupportsStandardParry, boolean impactGuard) {
        return paidNormalGuard && !parried && (impactGuard || !weaponSupportsStandardParry);
    }

    /**
     * Multiplier for a successful parry or guard. Defending against a
     * projectile scales it by the projectile multiplier, so a ranged attacker
     * is not knocked into a guard break from across the battlefield unless an
     * operator opts in. Invalid projectile multipliers disable projectile pressure.
     */
    public static float defenseMultiplier(float multiplier, boolean projectile,
                                          float projectileMultiplier) {
        if (!projectile) return multiplier;
        if (!Float.isFinite(projectileMultiplier) || projectileMultiplier <= 0.0F) return 0.0F;
        return multiplier * projectileMultiplier;
    }

    private GuardPosturePolicy() {}
}
