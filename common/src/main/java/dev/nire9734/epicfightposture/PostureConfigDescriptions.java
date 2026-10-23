package dev.nire9734.epicfightposture;

/** Shared server-config descriptions for both loaders. Keep generated TOML comments concise. */
public final class PostureConfigDescriptions {
    public static final String ATTACK_MULTIPLIER =
            "Attack Posture damage multiplier (1.0 = normal, 0.0 = none).";
    public static final String PARRY_MULTIPLIER =
            "Parry Posture damage multiplier (1.0 = normal, 0.0 = none).";
    public static final String PARRY_BASE_MAX =
            "Parry damage (% of max Posture) against mobs with low base health and armor.";
    public static final String PARRY_BASE_MIN =
            "Parry damage (% of max Posture) against mobs with high base health and armor.";
    public static final String BREAK_RESISTANCE_STEP =
            "Extra resistance after each guard break (0.5 = +50% per break).";
    public static final String BREAK_RESISTANCE_MAX =
            "Maximum guard-break resistance (2.0 = half Posture damage).";
    public static final String REGEN_DELAY =
            "Seconds without Posture damage before normal regeneration starts.";
    public static final String REGEN_RATE =
            "Normal Posture regenerated per second (% of maximum).";
    public static final String ATTACK_ENTITY_MULTIPLIERS =
            "Per-mob attack multiplier; replaces the global setting.";
    public static final String ATTACK_ENTITY_EXAMPLE =
            "Example: [\"cataclysm:ignis=0.25\"] (multiplier: 0.0-100.0).";
    public static final String PARRY_ENTITY_MULTIPLIERS =
            "Per-mob parry multiplier; replaces the global setting.";
    public static final String PARRY_ENTITY_EXAMPLE =
            "Example: [\"cataclysm:ignis=0.5\"] (multiplier: 0.0-100.0).";
    public static final String ENTITY_BLACKLIST =
            "Exclude mob types from EFPI Posture and its HUD.";
    public static final String ENTITY_BLACKLIST_EXAMPLE =
            "Example: [\"minecraft:zombie\"]";
    public static final String AUTOMATIC_COMPATIBILITY =
            "Automatically add Posture support to compatible mobs.";
    public static final String MOD_ALLOWLIST =
            "Only auto-support these mod IDs; [] allows all mods.";
    public static final String MOD_DENYLIST =
            "Do not auto-support these mod IDs; overrides the allowlist.";

    private PostureConfigDescriptions() {}
}
