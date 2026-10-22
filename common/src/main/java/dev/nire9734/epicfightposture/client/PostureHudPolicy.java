package dev.nire9734.epicfightposture.client;

/**
 * Shared visibility trigger for the overhead health/posture HUD.
 *
 * <p>Epic Fight's HURT mode also treats any status effect as a reason to show
 * the bar, while TARGET modes can show a completely untouched mob. For EFPI's
 * mob HUD, require an actual hit or a non-full health/posture state instead.
 * A fully healed and recovered mob becomes hidden again.</p>
 */
public final class PostureHudPolicy {
    private PostureHudPolicy() {}

    public static boolean hasCombatDamage(float health, float maxHealth,
                                          int hurtTime, boolean damagedPosture) {
        return hurtTime > 0 || health < maxHealth || damagedPosture;
    }
}
