package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-authoritative posture tuning and cached blacklist lookups. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PostureServerConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue GLOBAL_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MINIMUM_POSTURE;
    public static final ForgeConfigSpec.BooleanValue USE_ARMOR_SCALING;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    public static final ForgeConfigSpec SPEC;

    private static volatile Set<ResourceLocation> blacklist = Set.of();

    static {
        BUILDER.push("posture");
        GLOBAL_MULTIPLIER = BUILDER
                .comment("Global maximum posture multiplier. 1.0 = default.")
                .defineInRange("global_multiplier", 1.0D, 0.1D, 100.0D);
        MINIMUM_POSTURE = BUILDER
                .comment("Minimum maximum posture an eligible mob can have.")
                .defineInRange("minimum_posture", 1.0D, 0.1D, 10000.0D);
        USE_ARMOR_SCALING = BUILDER
                .comment("Whether armor and armor toughness affect maximum posture.")
                .define("use_armor_scaling", true);
        ENTITY_BLACKLIST = BUILDER
                .comment("Entities that will not use posture. Use namespace:entity IDs.")
                .defineListAllowEmpty(
                        "entity_blacklist",
                        List.of(),
                        value -> value instanceof String
                );
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    public static float globalMultiplier() {
        return GLOBAL_MULTIPLIER.get().floatValue();
    }

    public static float minimumPosture() {
        return MINIMUM_POSTURE.get().floatValue();
    }

    public static boolean useArmorScaling() {
        return USE_ARMOR_SCALING.get();
    }

    public static boolean isBlacklisted(ResourceLocation entityId) {
        return entityId != null && blacklist.contains(entityId);
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            refreshBlacklist();
        }
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            refreshBlacklist();
        }
    }

    public static void refreshBlacklist() {
        Set<ResourceLocation> parsed = new HashSet<>();
        for (String rawId : ENTITY_BLACKLIST.get()) {
            ResourceLocation entityId = ResourceLocation.tryParse(rawId);
            if (entityId == null) {
                LOGGER.warn("Ignoring invalid entity ID '{}' in posture.entity_blacklist", rawId);
                continue;
            }
            parsed.add(entityId);
        }
        blacklist = Set.copyOf(parsed);
    }

    private PostureServerConfig() {
    }
}
