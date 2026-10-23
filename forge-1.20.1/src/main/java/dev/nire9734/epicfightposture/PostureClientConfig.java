package dev.nire9734.epicfightposture;

import dev.nire9734.epicfightposture.client.PostureHudPolicy;
import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only presentation settings. These values are never synchronized by the server. */
public final class PostureClientConfig {
    public static final double DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER = 0.70D;

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.EnumValue<PostureHudPolicy.Visibility> MOB_HEALTH_BAR_VISIBILITY;
    public static final ForgeConfigSpec.DoubleValue EXECUTION_SOUND_VOLUME_MULTIPLIER;
    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("hud");
        MOB_HEALTH_BAR_VISIBILITY = BUILDER
                .comment("When to show HP and posture bars added by this mod.")
                .defineEnum("mob_health_bar_visibility", PostureHudPolicy.Visibility.DAMAGED_ONLY);
        BUILDER.pop();

        BUILDER.push("sound");
        EXECUTION_SOUND_VOLUME_MULTIPLIER = BUILDER
                .comment(
                        "Volume of execution sound effects.",
                        "1.0 = normal, 0.5 = half, 0.0 = muted."
                )
                .defineInRange(
                        "execution_sound_volume_multiplier",
                        DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER,
                        0.0D,
                        1.0D
                );
        BUILDER.pop();
        SPEC = BUILDER.build();
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
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        EXECUTION_SOUND_VOLUME_MULTIPLIER.set(clamped);
    }

    public static void save() {
        MOB_HEALTH_BAR_VISIBILITY.save();
        EXECUTION_SOUND_VOLUME_MULTIPLIER.save();
    }

    private PostureClientConfig() {
    }
}
