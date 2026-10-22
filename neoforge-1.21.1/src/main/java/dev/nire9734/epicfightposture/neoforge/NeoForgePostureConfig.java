package dev.nire9734.epicfightposture.neoforge;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge 1.21.1 server-side posture and compatibility settings.
 * Game logic is active; renderer parity and multiplayer client-policy
 * synchronization are still undergoing compatibility testing.
 */
public final class NeoForgePostureConfig {
    static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue MAXIMUM_POSTURE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue POSTURE_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PARRY_BASE_MAX_PERCENT;
    public static final ModConfigSpec.DoubleValue PARRY_BASE_MIN_PERCENT;
    public static final ModConfigSpec.DoubleValue POSTURE_BREAK_RESISTANCE_STEP;
    public static final ModConfigSpec.DoubleValue POSTURE_BREAK_RESISTANCE_MAX;
    public static final ModConfigSpec.DoubleValue REGEN_DELAY_SECONDS;
    public static final ModConfigSpec.DoubleValue REGEN_PERCENT_PER_SECOND;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> POSTURE_MULTIPLIERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    public static final ModConfigSpec.BooleanValue AUTOMATIC_COMPATIBILITY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_ALLOWLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_DENYLIST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("posture");
        MAXIMUM_POSTURE_MULTIPLIER = builder
                .comment("Multiplier for maximum Posture. Higher values make attack-based guard breaks harder.")
                .defineInRange("maximum_posture_multiplier", 1.0D, 0.1D, 100.0D);
        POSTURE_DAMAGE_MULTIPLIER = builder
                .comment("Multiplier for Posture damage from attacks, not parries.")
                .defineInRange("posture_damage_multiplier", 1.0D, 0.0D, 100.0D);
        PARRY_BASE_MAX_PERCENT = builder
                .comment("Maximum percentage of Posture removed by a parry against mobs with low base health and armor.")
                .defineInRange("parry_base_max_percent", 22.0D, 0.0D, 100.0D);
        PARRY_BASE_MIN_PERCENT = builder
                .comment("Parry percentage approached against mobs with high base health and armor.")
                .defineInRange("parry_base_min_percent", 8.0D, 0.0D, 100.0D);
        POSTURE_BREAK_RESISTANCE_STEP = builder
                .comment("Increase in resistance for each confirmed guard break.")
                .defineInRange("posture_break_resistance_step", 0.5D, 0.0D, 100.0D);
        POSTURE_BREAK_RESISTANCE_MAX = builder
                .comment("Maximum resistance multiplier after repeated guard breaks.")
                .defineInRange("posture_break_resistance_max", 2.0D, 1.0D, 100.0D);
        builder.pop();

        builder.push("regeneration");
        REGEN_DELAY_SECONDS = builder
                .comment("Seconds since the last actual Posture reduction before ordinary regeneration starts.",
                        "Both direct attacks and parries restart the delay when they reduce Posture.",
                        "HP damage without Posture loss does not restart it; guard-break recovery is separate.")
                .defineInRange("delay_seconds", 15.0D, 0.0D, 60.0D);
        REGEN_PERCENT_PER_SECOND = builder
                .comment("Percentage of maximum Posture restored each second after the delay.")
                .defineInRange("percent_per_second", 2.0D, 0.0D, 100.0D);
        builder.pop();

        builder.push("entities");
        POSTURE_MULTIPLIERS = builder
                .comment("Override a mob type's maximum Posture. Example: [\"cataclysm:ignis=0.25\"]")
                .defineListAllowEmpty("posture_multipliers", List.of(), () -> "", value -> value instanceof String);
        ENTITY_BLACKLIST = builder
                .comment("Mob types excluded from Posture. Example: [\"minecraft:zombie\"]")
                .defineListAllowEmpty("entity_blacklist", List.of(), () -> "", value -> value instanceof String);
        builder.pop();

        builder.push("compatibility");
        AUTOMATIC_COMPATIBILITY = builder
                .comment("Automatically add Posture support to compatible mobs without Epic Fight patches.")
                .define("automatic_compatibility", true);
        MOD_ALLOWLIST = builder
                .comment("Only automatically add support to these mod IDs. [] allows all mods.")
                .defineListAllowEmpty("mod_allowlist", List.of(), () -> "", value -> value instanceof String);
        MOD_DENYLIST = builder
                .comment("Do not automatically add support to these mod IDs. Overrides the allowlist.")
                .defineListAllowEmpty("mod_denylist", List.of(), () -> "", value -> value instanceof String);
        builder.pop();

        SPEC = builder.build();
    }

    private NeoForgePostureConfig() {}
}
