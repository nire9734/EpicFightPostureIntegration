package dev.nire9734.epicfightposture.neoforge;

import dev.nire9734.epicfightposture.client.PostureHudPolicy;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-side execution sound preference; never synchronized by a server. */
public final class NeoForgePostureClientConfig {
    public static final double DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER = 0.70D;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<PostureHudPolicy.Visibility> MOB_HEALTH_BAR_VISIBILITY;
    public static final ModConfigSpec.DoubleValue EXECUTION_SOUND_VOLUME_MULTIPLIER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("hud");
        MOB_HEALTH_BAR_VISIBILITY = builder
                .comment("Visibility of EFPI-owned overhead mob health/posture bars only.",
                        "Combat Evolution-patched mobs and ordinary Epic Fight HUDs are unaffected.")
                .defineEnum("mob_health_bar_visibility", PostureHudPolicy.Visibility.DAMAGED_ONLY);
        builder.pop();

        builder.push("sound");
        EXECUTION_SOUND_VOLUME_MULTIPLIER = builder
                .comment(
                        "Client-side volume multiplier for Combat Evolution execution sounds.",
                        "1.0 = original volume, 0.5 = half volume, 0.0 = muted.")
                .defineInRange("execution_sound_volume_multiplier",
                        DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER, 0.0D, 1.0D);
        builder.pop();
        SPEC = builder.build();
    }

    public static float executionSoundVolumeMultiplier() {
        try {
            return EXECUTION_SOUND_VOLUME_MULTIPLIER.get().floatValue();
        } catch (IllegalStateException ignored) {
            return (float) DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER;
        }
    }

    public static PostureHudPolicy.Visibility mobHealthBarVisibility() {
        try {
            return MOB_HEALTH_BAR_VISIBILITY.get();
        } catch (IllegalStateException ignored) {
            return PostureHudPolicy.Visibility.DAMAGED_ONLY;
        }
    }

    public static void setMobHealthBarVisibility(PostureHudPolicy.Visibility visibility) {
        MOB_HEALTH_BAR_VISIBILITY.set(visibility);
    }

    public static void setExecutionSoundVolumeMultiplier(double value) {
        EXECUTION_SOUND_VOLUME_MULTIPLIER.set(Math.max(0.0D, Math.min(1.0D, value)));
    }

    public static void save() {
        MOB_HEALTH_BAR_VISIBILITY.save();
        EXECUTION_SOUND_VOLUME_MULTIPLIER.save();
    }

    private NeoForgePostureClientConfig() {}
}
