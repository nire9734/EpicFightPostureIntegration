package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
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
    public static final ForgeConfigSpec.BooleanValue ENABLE_VANILLA_HUMANOID_COMPATIBILITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GECKOLIB_HUMANOID_COMPATIBILITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VANILLA_SPIDER_COMPATIBILITY;
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
                .comment(
                        "Entities excluded from EFPI posture and automatic compatibility behavior.",
                        "Use namespace:entity IDs."
                )
                .defineListAllowEmpty("entity_blacklist", List.of(), value -> value instanceof String);
        BUILDER.pop();

        BUILDER.push("compatibility");
        ENABLE_VANILLA_HUMANOID_COMPATIBILITY = BUILDER
                .comment(
                        "Enable EFPI's automatic compatibility for entities rendered with Minecraft HumanoidModel.",
                        "Default: true. Reload the world/server after changing this option."
                )
                .define("enable_vanilla_humanoid_compatibility", true);
        ENABLE_GECKOLIB_HUMANOID_COMPATIBILITY = BUILDER
                .comment(
                        "Enable EFPI's automatic compatibility for humanoid GeckoLib entity models.",
                        "Default: true. This has no effect when GeckoLib is not installed.",
                        "Reload the world/server after changing this option."
                )
                .define("enable_geckolib_humanoid_compatibility", true);
        ENABLE_VANILLA_SPIDER_COMPATIBILITY = BUILDER
                .comment(
                        "Enable EFPI's automatic compatibility for unpatched entities rendered with Minecraft SpiderModel.",
                        "The source mob keeps its original AI; EFPI uses a lightweight SPIDER patch only for posture/execution states.",
                        "Default: true. Reload the world/server after changing this option."
                )
                .define("enable_vanilla_spider_compatibility", true);
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

    public static boolean vanillaHumanoidCompatibilityEnabled() {
        return ENABLE_VANILLA_HUMANOID_COMPATIBILITY.get();
    }

    public static boolean geckoLibHumanoidCompatibilityEnabled() {
        return ENABLE_GECKOLIB_HUMANOID_COMPATIBILITY.get();
    }

    public static boolean vanillaSpiderCompatibilityEnabled() {
        return ENABLE_VANILLA_SPIDER_COMPATIBILITY.get();
    }

    /** Treat an EFPI-owned automatic compatibility target as disabled when its switch is off. */
    public static boolean isBlacklisted(ResourceLocation entityId) {
        if (entityId == null) {
            return false;
        }
        if (blacklist.contains(entityId)) {
            return true;
        }
        return VanillaHumanoidCompat.isRegisteredByEfpi(entityId)
                && !VanillaHumanoidCompat.isCompatibilityEnabled(entityId);
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
