package dev.nire9734.epicfightposture;

/** Pure normal-guard eligibility shared by both Epic Fight loader adapters. */
public final class GuardPosturePolicy {
    public static boolean shouldApply(boolean paidNormalGuard, boolean parried,
                                      boolean weaponSupportsStandardParry) {
        return paidNormalGuard && !parried && !weaponSupportsStandardParry;
    }

    private GuardPosturePolicy() {}
}
