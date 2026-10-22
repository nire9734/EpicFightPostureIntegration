package dev.nire9734.epicfightposture;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only presentation settings. These values are never synchronized by the server. */
public final class PostureClientConfig {
    public static final double DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER = 0.70D;

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue EXECUTION_SOUND_VOLUME_MULTIPLIER;
    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("sound");
        EXECUTION_SOUND_VOLUME_MULTIPLIER = BUILDER
                .comment(
                        "Client-side volume multiplier for Combat Evolution execution sound effects.",
                        "1.0 = Combat Evolution's original volume, 0.5 = half volume, 0.0 = muted."
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

    public static void setExecutionSoundVolumeMultiplier(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        EXECUTION_SOUND_VOLUME_MULTIPLIER.set(clamped);
    }

    public static void save() {
        EXECUTION_SOUND_VOLUME_MULTIPLIER.save();
    }

    private PostureClientConfig() {
    }
}
