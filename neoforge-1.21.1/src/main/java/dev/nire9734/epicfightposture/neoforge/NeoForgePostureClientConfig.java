package dev.nire9734.epicfightposture.neoforge;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.PostureConfigEntries;
import dev.nire9734.epicfightposture.client.PostureHudPolicy;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

/** Client-only presentation settings and additive compatibility safety exclusions. */
public final class NeoForgePostureClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final double DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER = 0.70D;

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<PostureHudPolicy.Visibility> MOB_HEALTH_BAR_VISIBILITY;
    public static final ModConfigSpec.DoubleValue EXECUTION_SOUND_VOLUME_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LOCAL_ENTITY_BLACKLIST;
    private static volatile Set<ResourceLocation> localEntityBlacklist = Set.of();
    private static boolean listenersRegistered;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("hud");
        MOB_HEALTH_BAR_VISIBILITY = builder
                .comment("When to show HP and posture bars added by this mod.")
                .defineEnum("mob_health_bar_visibility", PostureHudPolicy.Visibility.DAMAGED_ONLY);
        builder.pop();

        builder.push("sound");
        EXECUTION_SOUND_VOLUME_MULTIPLIER = builder
                .comment(
                        "Volume of execution sound effects.",
                        "1.0 = normal, 0.5 = half, 0.0 = muted.")
                .defineInRange("execution_sound_volume_multiplier",
                        DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER, 0.0D, 1.0D);
        builder.pop();

        builder.push("compatibility");
        LOCAL_ENTITY_BLACKLIST = builder
                .comment("Extra local safety exclusions for automatic Epic Fight patches.",
                        "Entries are entity IDs such as craft_of_the_wild:stone_talus.",
                        "These exclusions apply before server settings synchronize.",
                        "They cannot override a server blacklist. Restart to remove existing registrations.")
                .defineListAllowEmpty("local_entity_blacklist", List.of(), () -> "",
                        value -> value instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    public static synchronized void register(IEventBus modBus) {
        if (listenersRegistered) return;
        listenersRegistered = true;
        modBus.addListener(NeoForgePostureClientConfig::onLoading);
        modBus.addListener(NeoForgePostureClientConfig::onReloading);
    }

    private static void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) refreshLocalExclusions();
    }

    private static void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) refreshLocalExclusions();
    }

    private static void refreshLocalExclusions() {
        localEntityBlacklist = PostureConfigEntries.entityBlacklist(LOCAL_ENTITY_BLACKLIST.get(),
                ResourceLocation::tryParse,
                raw -> LOGGER.warn("Ignoring invalid local compatibility blacklist entry '{}'", raw));
    }

    public static boolean isLocallyExcluded(ResourceLocation id) {
        return id != null && localEntityBlacklist.contains(id);
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
