package dev.nire9734.epicfightposture;

/**
 * Shared server-config comments for both loaders. One sentence each, ending
 * with an example of the value's scale. Defaults are not repeated here; the
 * README's configuration table lists them. Use "guard break" for a Posture
 * break and "Posture" (capitalized) for the resource.
 */
public final class PostureConfigDescriptions {
    public static final String ATTACK_MULTIPLIER =
            "Posture damage from attacks (1.0 = normal, 0.0 = none).";
    public static final String PARRY_MULTIPLIER =
            "Posture damage from parries (1.0 = normal, 0.0 = none).";
    public static final String GUARD_ENABLED =
            "Let successful normal guards damage the attacker's Posture (Guard: only weapons that cannot parry; Impact Guard: all weapons).";
    public static final String GUARD_MULTIPLIER =
            "Posture damage from normal guards, relative to a parry (0.25 = 25%).";
    public static final String PROJECTILE_DEFENSE_MULTIPLIER =
            "Posture damage from parrying or guarding projectiles, relative to melee (0.0 = none, 1.0 = same).";
    public static final String PARRY_BASE_MAX =
            "Parry damage against weak mobs (low base health and armor), in % of max Posture.";
    public static final String PARRY_BASE_MIN =
            "Parry damage against strong mobs (high base health and armor), in % of max Posture.";
    public static final String BREAK_RESISTANCE_STEP =
            "Extra Posture resistance gained per guard break (0.5 = +50% per guard break).";
    public static final String BREAK_RESISTANCE_MAX =
            "Maximum guard break resistance (2.0 = Posture damage halved).";
    public static final String REGEN_DELAY =
            "Seconds without Posture damage before regeneration starts.";
    public static final String REGEN_RATE =
            "Posture regenerated per second, in % of max Posture.";
    public static final String ATTACK_ENTITY_MULTIPLIERS =
            "Per-mob attack multipliers; each entry replaces the global attack multiplier.";
    public static final String ATTACK_ENTITY_EXAMPLE =
            "Format: [\"modid:mob=multiplier\"], e.g. [\"cataclysm:ignis=0.25\"] (0.0-100.0).";
    public static final String PARRY_ENTITY_MULTIPLIERS =
            "Per-mob parry multipliers; each entry replaces the global parry multiplier.";
    public static final String PARRY_ENTITY_EXAMPLE =
            "Format: [\"modid:mob=multiplier\"], e.g. [\"cataclysm:ignis=0.5\"] (0.0-100.0).";
    public static final String ENTITY_BLACKLIST =
            "Mob types excluded from Posture, automatic patches and the health/Posture HUD."
                    + " Clients also apply it before server settings arrive.";
    public static final String ENTITY_BLACKLIST_EXAMPLE =
            "Example: [\"craft_of_the_wild:stone_talus\"]";
    public static final String AUTOMATIC_COMPATIBILITY =
            "Automatically add Posture support to compatible mobs."
                    + " false on a client disables it there even if the server enables it.";
    public static final String MOD_ALLOWLIST =
            "Only add automatic support for these mod IDs ([] = all mods).";
    public static final String MOD_DENYLIST =
            "Never add automatic support for these mod IDs; takes priority over the allowlist.";

    private PostureConfigDescriptions() {}
}
