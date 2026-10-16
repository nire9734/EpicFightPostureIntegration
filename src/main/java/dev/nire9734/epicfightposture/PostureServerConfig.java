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
    public static final ForgeConfigSpec.DoubleValue ATTACK_DAMAGE_REFERENCE;
    public static final ForgeConfigSpec.DoubleValue ATTACK_DAMAGE_SCALING_EXPONENT;
    public static final ForgeConfigSpec.DoubleValue PARRY_POSTURE_PERCENT;
    public static final ForgeConfigSpec.DoubleValue FULL_HEALTH_POSTURE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue ZERO_HEALTH_POSTURE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue HEALTH_POSTURE_CURVE_EXPONENT;
    public static final ForgeConfigSpec.DoubleValue HEALTH_SCALING_EHP_START;
    public static final ForgeConfigSpec.DoubleValue HEALTH_SCALING_EHP_END;
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
                        "Default: 0.55. Final posture damage is also multiplied by dealt-damage scaling and target-health scaling below."
                )
                .defineInRange("posture_damage_multiplier", 0.55D, 0.0D, 100.0D);
        ATTACK_DAMAGE_REFERENCE = BUILDER
                .comment(
                        "Final dealt damage at which direct-attack posture scaling is x1.0.",
                        "Default: 8.0, matching EFPI's reference damage used for maximum-posture EHP scaling."
                )
                .defineInRange("attack_damage_reference", 8.0D, 0.1D, 100000.0D);
        ATTACK_DAMAGE_SCALING_EXPONENT = BUILDER
                .comment(
                        "Exponent for direct-attack dealt-damage scaling: (dealtDamage / reference)^exponent.",
                        "Default: 0.7, matching the exponent used by maximum-posture EHP scaling.",
                        "Set to 0.0 to ignore dealt-damage scaling while still using Impact."
                )
                .defineInRange("attack_damage_scaling_exponent", 0.7D, 0.0D, 4.0D);
        PARRY_POSTURE_PERCENT = BUILDER
                .comment(
                        "Base parry posture damage as a percentage of the target's maximum posture.",
                        "Default: 8.0. A minimum raw parry posture damage of 0.5 is retained before target-health scaling."
                )
                .defineInRange("parry_posture_percent", 8.0D, 0.0D, 100.0D);
        FULL_HEALTH_POSTURE_DAMAGE_MULTIPLIER = BUILDER
                .comment(
                        "Posture-damage multiplier applied at 100% target health when the EHP-based finisher curve is fully active.",
                        "Default: 0.25. High-EHP enemies are deliberately difficult to posture-break at high health."
                )
                .defineInRange("full_health_posture_damage_multiplier", 0.25D, 0.0D, 100.0D);
        ZERO_HEALTH_POSTURE_DAMAGE_MULTIPLIER = BUILDER
                .comment(
                        "Posture-damage multiplier approached as target health falls toward 0% when the finisher curve is active.",
                        "Default: 1.0."
                )
                .defineInRange("zero_health_posture_damage_multiplier", 1.0D, 0.0D, 100.0D);
        HEALTH_POSTURE_CURVE_EXPONENT = BUILDER
                .comment(
                        "Exponent controlling how strongly posture vulnerability is delayed toward low health.",
                        "1.0 = linear. Values above 1.0 keep posture damage lower until later in the fight. Default: 2.0."
                )
                .defineInRange("health_posture_curve_exponent", 2.0D, 0.01D, 100.0D);
        HEALTH_SCALING_EHP_START = BUILDER
                .comment(
                        "Effective health at or below which the health-based finisher posture modifier is disabled.",
                        "Low-EHP mobs use a neutral x1.0 posture-damage multiplier. Default: 40.0."
                )
                .defineInRange("health_scaling_ehp_start", 40.0D, 0.0D, 1000000.0D);
        HEALTH_SCALING_EHP_END = BUILDER
                .comment(
                        "Effective health at or above which the configured health-based finisher curve is fully applied.",
                        "Between the start and end values, curve strength is smoothly interpolated. Default: 200.0."
                )
                .defineInRange("health_scaling_ehp_end", 200.0D, 0.0D, 1000000.0D);
        MINIMUM_POSTURE = BUILDER
                .comment("Minimum maximum posture an eligible mob can have.")
                .defineInRange("minimum_posture", 1.0D, 0.1D, 10000.0D);
        USE_ARMOR_SCALING = BUILDER
                .comment("Whether armor and armor toughness affect maximum posture and EHP-based finisher scaling.")
                .define("use_armor_scaling", true);

        BUILDER.push("regeneration");
        REGEN_DELAY_SECONDS = BUILDER
                .comment(
                        "Seconds after posture damage or a parry before posture regeneration starts.",
                        "Default: 10.0 seconds, so ordinary boss attack gaps and long telegraphs usually do not trigger regeneration."
                )
                .defineInRange("delay_seconds", 10.0D, 0.0D, 60.0D);
        REGEN_RATE_PERCENT = BUILDER
                .comment(
                        "Maximum-posture percentage regenerated per second after the delay.",
                        "Default: 2.0. Regeneration is constant and does not vary with target health."
                )
                .defineInRange("percent_per_second", 2.0D, 0.0D, 100.0D);
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

    public static float attackDamageReference() {
        return ATTACK_DAMAGE_REFERENCE.get().floatValue();
    }

    public static float attackDamageScalingExponent() {
        return ATTACK_DAMAGE_SCALING_EXPONENT.get().floatValue();
    }

    /** Base parry posture damage as a fraction of target maximum posture. */
    public static float parryPostureFraction() {
        return PARRY_POSTURE_PERCENT.get().floatValue() / 100.0F;
    }

    public static float fullHealthPostureDamageMultiplier() {
        return FULL_HEALTH_POSTURE_DAMAGE_MULTIPLIER.get().floatValue();
    }

    public static float zeroHealthPostureDamageMultiplier() {
        return ZERO_HEALTH_POSTURE_DAMAGE_MULTIPLIER.get().floatValue();
    }

    public static float healthPostureCurveExponent() {
        return HEALTH_POSTURE_CURVE_EXPONENT.get().floatValue();
    }

    public static float healthScalingEhpStart() {
        return HEALTH_SCALING_EHP_START.get().floatValue();
    }

    public static float healthScalingEhpEnd() {
        return HEALTH_SCALING_EHP_END.get().floatValue();
    }

    /** Server-side delay before ordinary posture regeneration begins. */
    public static float postureRegenDelaySeconds() {
        return REGEN_DELAY_SECONDS.get().floatValue();
    }

    /** Constant fraction of maximum posture regenerated per second after the delay. */
    public static float postureRegenFractionPerSecond() {
        return REGEN_RATE_PERCENT.get().floatValue() / 100.0F;
    }

    // Internal compatibility adapters: the runtime still asks for health anchors,
    // but all anchors intentionally resolve to the same constant regeneration rate.
    public static float postureRegenFullHealthFractionPerSecond() {
        return postureRegenFractionPerSecond();
    }

    public static float postureRegen75HealthFractionPerSecond() {
        return postureRegenFractionPerSecond();
    }

    public static float postureRegen50HealthFractionPerSecond() {
        return postureRegenFractionPerSecond();
    }

    public static float postureRegen25HealthFractionPerSecond() {
        return postureRegenFractionPerSecond();
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
