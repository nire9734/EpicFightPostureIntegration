package dev.nire9734.epicfightposture.neoforge;

import dev.nire9734.epicfightposture.PostureConfigDescriptions;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge 1.21.1 server-side posture and compatibility settings.
 * Gameplay and multiplayer server-policy synchronization are implemented;
 * cross-version renderer parity still requires live compatibility testing.
 */
public final class NeoForgePostureConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue ATTACK_POSTURE_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PARRY_POSTURE_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PARRY_BASE_MAX_PERCENT;
    public static final ModConfigSpec.DoubleValue PARRY_BASE_MIN_PERCENT;
    public static final ModConfigSpec.DoubleValue POSTURE_BREAK_RESISTANCE_STEP;
    public static final ModConfigSpec.DoubleValue POSTURE_BREAK_RESISTANCE_MAX;
    public static final ModConfigSpec.DoubleValue REGEN_DELAY_SECONDS;
    public static final ModConfigSpec.DoubleValue REGEN_PERCENT_PER_SECOND;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ATTACK_POSTURE_DAMAGE_MULTIPLIERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PARRY_POSTURE_DAMAGE_MULTIPLIERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    public static final ModConfigSpec.BooleanValue AUTOMATIC_COMPATIBILITY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_ALLOWLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_DENYLIST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("posture");
        ATTACK_POSTURE_DAMAGE_MULTIPLIER = builder
                .comment(PostureConfigDescriptions.ATTACK_MULTIPLIER)
                .defineInRange("attack_posture_damage_multiplier", 1.0D, 0.0D, 100.0D);
        PARRY_POSTURE_DAMAGE_MULTIPLIER = builder
                .comment(PostureConfigDescriptions.PARRY_MULTIPLIER)
                .defineInRange("parry_posture_damage_multiplier", 1.0D, 0.0D, 100.0D);
        PARRY_BASE_MAX_PERCENT = builder
                .comment(PostureConfigDescriptions.PARRY_BASE_MAX)
                .defineInRange("parry_base_max_percent", 22.0D, 0.0D, 100.0D);
        PARRY_BASE_MIN_PERCENT = builder
                .comment(PostureConfigDescriptions.PARRY_BASE_MIN)
                .defineInRange("parry_base_min_percent", 8.0D, 0.0D, 100.0D);
        POSTURE_BREAK_RESISTANCE_STEP = builder
                .comment(PostureConfigDescriptions.BREAK_RESISTANCE_STEP)
                .defineInRange("posture_break_resistance_step", 0.5D, 0.0D, 100.0D);
        POSTURE_BREAK_RESISTANCE_MAX = builder
                .comment(PostureConfigDescriptions.BREAK_RESISTANCE_MAX)
                .defineInRange("posture_break_resistance_max", 2.0D, 1.0D, 100.0D);
        builder.pop();

        builder.push("regeneration");
        REGEN_DELAY_SECONDS = builder
                .comment(PostureConfigDescriptions.REGEN_DELAY)
                .defineInRange("delay_seconds", 15.0D, 0.0D, 60.0D);
        REGEN_PERCENT_PER_SECOND = builder
                .comment(PostureConfigDescriptions.REGEN_RATE)
                .defineInRange("percent_per_second", 2.0D, 0.0D, 100.0D);
        builder.pop();

        builder.push("entities");
        ATTACK_POSTURE_DAMAGE_MULTIPLIERS = builder
                .comment(PostureConfigDescriptions.ATTACK_ENTITY_MULTIPLIERS,
                        PostureConfigDescriptions.ATTACK_ENTITY_EXAMPLE)
                .defineListAllowEmpty("attack_posture_damage_multipliers", List.of(), () -> "", value -> value instanceof String);
        PARRY_POSTURE_DAMAGE_MULTIPLIERS = builder
                .comment(PostureConfigDescriptions.PARRY_ENTITY_MULTIPLIERS,
                        PostureConfigDescriptions.PARRY_ENTITY_EXAMPLE)
                .defineListAllowEmpty("parry_posture_damage_multipliers", List.of(), () -> "", value -> value instanceof String);
        ENTITY_BLACKLIST = builder
                .comment(PostureConfigDescriptions.ENTITY_BLACKLIST,
                        PostureConfigDescriptions.ENTITY_BLACKLIST_EXAMPLE)
                .defineListAllowEmpty("entity_blacklist", List.of(), () -> "", value -> value instanceof String);
        builder.pop();

        builder.push("compatibility");
        AUTOMATIC_COMPATIBILITY = builder
                .comment(PostureConfigDescriptions.AUTOMATIC_COMPATIBILITY)
                .define("automatic_compatibility", true);
        MOD_ALLOWLIST = builder
                .comment(PostureConfigDescriptions.MOD_ALLOWLIST)
                .defineListAllowEmpty("mod_allowlist", List.of(), () -> "", value -> value instanceof String);
        MOD_DENYLIST = builder
                .comment(PostureConfigDescriptions.MOD_DENYLIST)
                .defineListAllowEmpty("mod_denylist", List.of(), () -> "", value -> value instanceof String);
        builder.pop();

        SPEC = builder.build();
    }

    private NeoForgePostureConfig() {}
}
