package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.util.thread.EffectiveSide;
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

    private static final Snapshot CLIENT_PERMISSIVE_DEFAULTS = new Snapshot(
            1.0D,
            1.0D,
            true,
            Set.of(),
            true,
            true,
            true
    );

    private static volatile Set<ResourceLocation> blacklist = Set.of();
    private static volatile Snapshot remoteSnapshot;

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
                        "Default: true. Restart the server after changing this option."
                )
                .define("enable_vanilla_humanoid_compatibility", true);
        ENABLE_GECKOLIB_HUMANOID_COMPATIBILITY = BUILDER
                .comment(
                        "Enable EFPI's automatic compatibility for humanoid GeckoLib entity models.",
                        "Default: true. This has no effect when GeckoLib is not installed.",
                        "Restart the server after changing this option."
                )
                .define("enable_geckolib_humanoid_compatibility", true);
        ENABLE_VANILLA_SPIDER_COMPATIBILITY = BUILDER
                .comment(
                        "Enable EFPI's automatic compatibility for unpatched entities rendered with Minecraft SpiderModel.",
                        "The source mob keeps its original AI; EFPI uses a lightweight SPIDER patch only for posture/execution states.",
                        "Default: true. Restart the server after changing this option."
                )
                .define("enable_vanilla_spider_compatibility", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static float globalMultiplier() {
        Snapshot snapshot = effectiveClientSnapshot();
        return (snapshot != null ? snapshot.globalMultiplier() : GLOBAL_MULTIPLIER.get()).floatValue();
    }

    public static float minimumPosture() {
        Snapshot snapshot = effectiveClientSnapshot();
        return (snapshot != null ? snapshot.minimumPosture() : MINIMUM_POSTURE.get()).floatValue();
    }

    public static boolean useArmorScaling() {
        Snapshot snapshot = effectiveClientSnapshot();
        return snapshot != null ? snapshot.useArmorScaling() : USE_ARMOR_SCALING.get();
    }

    public static boolean vanillaHumanoidCompatibilityEnabled() {
        Snapshot snapshot = effectiveClientSnapshot();
        return snapshot != null
                ? snapshot.enableVanillaHumanoidCompatibility()
                : ENABLE_VANILLA_HUMANOID_COMPATIBILITY.get();
    }

    public static boolean geckoLibHumanoidCompatibilityEnabled() {
        Snapshot snapshot = effectiveClientSnapshot();
        return snapshot != null
                ? snapshot.enableGeckoLibHumanoidCompatibility()
                : ENABLE_GECKOLIB_HUMANOID_COMPATIBILITY.get();
    }

    public static boolean vanillaSpiderCompatibilityEnabled() {
        Snapshot snapshot = effectiveClientSnapshot();
        return snapshot != null
                ? snapshot.enableVanillaSpiderCompatibility()
                : ENABLE_VANILLA_SPIDER_COMPATIBILITY.get();
    }

    public static boolean isExplicitlyBlacklisted(ResourceLocation entityId) {
        if (entityId == null) {
            return false;
        }
        Snapshot snapshot = effectiveClientSnapshot();
        return snapshot != null
                ? snapshot.entityBlacklist().contains(entityId)
                : blacklist.contains(entityId);
    }

    /** Treat an EFPI-owned automatic compatibility target as disabled when its switch is off. */
    public static boolean isBlacklisted(ResourceLocation entityId) {
        if (entityId == null) {
            return false;
        }
        if (isExplicitlyBlacklisted(entityId)) {
            return true;
        }
        return VanillaHumanoidCompat.isRegisteredByEfpi(entityId)
                && !VanillaHumanoidCompat.isCompatibilityEnabled(entityId);
    }

    /** Snapshot of the local authoritative config, used for login synchronization. */
    public static Snapshot localSnapshot() {
        return new Snapshot(
                GLOBAL_MULTIPLIER.get(),
                MINIMUM_POSTURE.get(),
                USE_ARMOR_SCALING.get(),
                blacklist,
                ENABLE_VANILLA_HUMANOID_COMPATIBILITY.get(),
                ENABLE_GECKOLIB_HUMANOID_COMPATIBILITY.get(),
                ENABLE_VANILLA_SPIDER_COMPATIBILITY.get()
        );
    }

    /** Installs server-authoritative settings received by a logical client. */
    public static void applyRemoteSnapshot(Snapshot snapshot) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            remoteSnapshot = snapshot;
        }
    }

    /** Clears settings from the previous server when the client level is unloaded. */
    public static void clearRemoteSnapshot() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            remoteSnapshot = null;
        }
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

    private static Snapshot effectiveClientSnapshot() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return null;
        }
        try {
            if (EffectiveSide.get() != LogicalSide.CLIENT) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
        Snapshot snapshot = remoteSnapshot;
        return snapshot != null ? snapshot : CLIENT_PERMISSIVE_DEFAULTS;
    }

    public record Snapshot(
            Double globalMultiplier,
            Double minimumPosture,
            boolean useArmorScaling,
            Set<ResourceLocation> entityBlacklist,
            boolean enableVanillaHumanoidCompatibility,
            boolean enableGeckoLibHumanoidCompatibility,
            boolean enableVanillaSpiderCompatibility) {
        public Snapshot {
            globalMultiplier = globalMultiplier == null ? 1.0D : globalMultiplier;
            minimumPosture = minimumPosture == null ? 1.0D : minimumPosture;
            entityBlacklist = entityBlacklist == null ? Set.of() : Set.copyOf(entityBlacklist);
        }
    }

    private PostureServerConfig() {
    }
}
