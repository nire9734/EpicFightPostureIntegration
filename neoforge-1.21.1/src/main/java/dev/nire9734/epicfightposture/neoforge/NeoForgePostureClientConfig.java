package dev.nire9734.epicfightposture.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-side execution sound preference; never synchronized by a server. */
public final class NeoForgePostureClientConfig {
    public static final double DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER = 0.70D;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue EXECUTION_SOUND_VOLUME_MULTIPLIER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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

    public static void setExecutionSoundVolumeMultiplier(double value) {
        EXECUTION_SOUND_VOLUME_MULTIPLIER.set(Math.max(0.0D, Math.min(1.0D, value)));
    }

    public static void save() {
        EXECUTION_SOUND_VOLUME_MULTIPLIER.save();
    }

    private NeoForgePostureClientConfig() {}
}
