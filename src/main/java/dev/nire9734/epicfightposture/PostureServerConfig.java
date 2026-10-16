package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import dev.nire9734.epicfightposture.network.BridgeNetwork;
import dev.nire9734.epicfightposture.network.ServerSettingsSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.server.ServerLifecycleHooks;
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
    public static final ForgeConfigSpec.DoubleValue POSTURE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue PARRY_POSTURE_PERCENT;
    public static final ForgeConfigSpec.DoubleValue LOW_HEALTH_POSTURE_DAMAGE_BONUS_PERCENT;
    public static final ForgeConfigSpec.DoubleValue MINIMUM_POSTURE;
    public static final ForgeConfigSpec.BooleanValue USE_ARMOR_SCALING;
    public static final ForgeConfigSpec.DoubleValue REGEN_DELAY_SECONDS;
    public static final ForgeConfigSpec.DoubleValue REGEN_RATE_PERCENT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VANILLA_HUMANOID_COMPATIBILITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GECKOLIB_HUMANOID_COMPATIBILITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VANILLA_SPIDER_COMPATIBILITY;
    public static final ForgeConfigSpec SPEC;

    private static final Snapshot CLIENT_FAIL_CLOSED_DEFAULTS = new Snapshot(
            1.0D,
            1.0D,
            true,
            Set.of(),
            false,
            false,
            false
    );

    private static volatile Set<ResourceLocation> blacklist = Set.of();
    private static volatile Snapshot remoteSnapshot;

    static {
        BUILDER.push("posture");
        GLOBAL_MULTIPLIER = BUILDER
                .comment("Global maximum posture multiplier. 1.0 = default.")
                .defineInRange("global_multiplier", 1.0D, 0.1D, 100.0D);
        POSTURE_DAMAGE_MULTIPLIER = BUILDER
                .comment(
                        "Base multiplier applied to Impact-derived posture damage from direct player attacks.",
                        "Default: 0.55. Final posture damage is also multiplied by the target-health scaling below."
                )
                .defineInRange("posture_damage_multiplier", 0.55D, 0.0D, 100.0D);
        PARRY_POSTURE_PERCENT = BUILDER
                .comment(
                        "Base parry posture damage as a percentage of the target's maximum posture.",
                        "Default: 6.0. A minimum raw parry posture damage of 0.5 is retained for low-posture mobs.",
                        "Target-health scaling is applied after this base parry value is calculated."
                )
                .defineInRange("parry_posture_percent", 6.0D, 0.0D, 100.0D);
        LOW_HEALTH_POSTURE_DAMAGE_BONUS_PERCENT = BUILDER
                .comment(
                        "Maximum bonus to posture damage from both direct attacks and parries as target health falls.",
                        "The bonus scales linearly from 0% at full health to this value at 0 health.",
                        "Default: 50.0, giving x1.0 at full health, x1.25 at 50% health, and x1.5 near 0 health."
                )
                .defineInRange("low_health_posture_damage_bonus_percent", 50.0D, 0.0D, 1000.0D);
        MINIMUM_POSTURE = BUILDER
                .comment("Minimum maximum posture an eligible mob can have.")
                .defineInRange("minimum_posture", 1.0D, 0.1D, 10000.0D);
        USE_ARMOR_SCALING = BUILDER
                .comment("Whether armor and armor toughness affect maximum posture.")
                .define("use_armor_scaling", true);

        BUILDER.push("regeneration");
        REGEN_DELAY_SECONDS = BUILDER
                .comment(
                        "Seconds after posture damage or a parry before posture regeneration starts.",
                        "Default: 5.0 seconds."
                )
                .defineInRange("delay_seconds", 5.0D, 0.0D, 60.0D);
        REGEN_RATE_PERCENT = BUILDER
                .comment(
                        "Maximum-posture percentage regenerated per second after the delay.",
                        "This rate is independent of target health. Default: 4.0 (% per second)."
                )
                .defineInRange("percent_per_second", 4.0D, 0.0D, 100.0D);
        BUILDER.pop();

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
                        "Enable EFPI's automatic compatibility for supported extended humanoid model backends, including ModelPart, GeckoLib, Lionfish, and AzureLib.",
                        "Default: true. Restart the server after changing this option."
                )
                .define("enable_extended_humanoid_compatibility", true);
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

    /** Server-side tuning for Impact-derived posture damage. */
    public static float postureDamageMultiplier() {
        return POSTURE_DAMAGE_MULTIPLIER.get().floatValue();
    }

    /** Base parry posture damage as a fraction of target maximum posture. */
    public static float parryPostureFraction() {
        return PARRY_POSTURE_PERCENT.get().floatValue() / 100.0F;
    }

    /** Maximum extra posture-damage multiplier contribution at zero target health. */
    public static float lowHealthPostureDamageBonusFraction() {
        return LOW_HEALTH_POSTURE_DAMAGE_BONUS_PERCENT.get().floatValue() / 100.0F;
    }

    /** Server-side delay before ordinary posture regeneration begins. */
    public static float postureRegenDelaySeconds() {
        return REGEN_DELAY_SECONDS.get().floatValue();
    }

    /** Constant ordinary posture regeneration rate as a fraction of maximum posture per second. */
    public static float postureRegenFractionPerSecond() {
        return REGEN_RATE_PERCENT.get().floatValue() / 100.0F;
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
            synchronizeReloadedSettings();
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

    private static void synchronizeReloadedSettings() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        server.execute(() -> {
            ServerSettingsSyncPacket packet = ServerSettingsSyncPacket.current();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                BridgeNetwork.sendPlayer(player, packet);
            }
        });
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
        return snapshot != null ? snapshot : CLIENT_FAIL_CLOSED_DEFAULTS;
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
