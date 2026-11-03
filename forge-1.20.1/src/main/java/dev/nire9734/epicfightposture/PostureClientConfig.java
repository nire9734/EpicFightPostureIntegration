package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.client.PostureHudPolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;

/** Client-only presentation settings and additive compatibility safety exclusions. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PostureClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final double DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER = 0.70D;

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.EnumValue<PostureHudPolicy.Visibility> MOB_HEALTH_BAR_VISIBILITY;
    public static final ForgeConfigSpec.DoubleValue EXECUTION_SOUND_VOLUME_MULTIPLIER;
    /** A local safety opt-out. It never grants compatibility against a server's policy. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOCAL_ENTITY_BLACKLIST;
    public static final ForgeConfigSpec SPEC;
    private static volatile Set<ResourceLocation> localEntityBlacklist = Set.of();

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

        BUILDER.push("compatibility");
        LOCAL_ENTITY_BLACKLIST = BUILDER
                .comment("Extra local safety exclusions for automatic Epic Fight patches.",
                        "Entries are entity IDs such as craft_of_the_wild:stone_talus.",
                        "These exclusions apply before server settings synchronize.",
                        "They cannot override a server blacklist. Restart to remove existing registrations.")
                .defineListAllowEmpty("local_entity_blacklist", List.of(), value -> value instanceof String);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    public static void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) refreshLocalExclusions();
    }

    @SubscribeEvent
    public static void onReloading(ModConfigEvent.Reloading event) {
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
