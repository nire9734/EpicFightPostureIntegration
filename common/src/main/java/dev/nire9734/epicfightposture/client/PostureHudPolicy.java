package dev.nire9734.epicfightposture.client;

/** Shared visibility rules for HUDs owned by EFPI, independent of Epic Fight's config. */
public final class PostureHudPolicy {
    public enum Visibility {
        ALWAYS,
        DAMAGED_ONLY,
        HIDDEN
    }

    private PostureHudPolicy() {}

    public static boolean shouldDisplay(Visibility visibility, float health, float maxHealth,
                                        int hurtTime, boolean damagedPosture) {
        return switch (visibility) {
            case ALWAYS -> true;
            case DAMAGED_ONLY -> hasCombatDamage(health, maxHealth, hurtTime, damagedPosture);
            case HIDDEN -> false;
        };
    }

    /** Any recent hit or non-full health/posture is enough; status effects alone are not. */
    public static boolean hasCombatDamage(float health, float maxHealth,
                                          int hurtTime, boolean damagedPosture) {
        return hurtTime > 0 || health < maxHealth || damagedPosture;
    }
}
