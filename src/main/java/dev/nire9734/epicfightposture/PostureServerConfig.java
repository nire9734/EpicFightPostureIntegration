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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-authoritative posture tuning and cached compatibility policy. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PostureServerConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /*
     * Parry-focus balance uses observed HP depletion with an internal 0.54
     * attack coefficient and public multiplier defaulting to 1.0.
     */
    private static final float BASE_ATTACK_POSTURE_COEFFICIENT = 0.54F;
    private static final float MINIMUM_POSTURE = 1.0F;
    private static final boolean USE_ARMOR_SCALING = true;

    /**
     * Retained as an internal network representation for protocol 3. The public
     * config no longer exposes a filter mode; an empty allowlist means unrestricted.
     */
    public enum CompatibilityFilterMode {
        DENYLIST,
        ALLOWLIST
    }

    public static final ForgeConfigSpec.DoubleValue MAXIMUM_POSTURE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue POSTURE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue PARRY_BASE_MAX_PERCENT;
    public static final ForgeConfigSpec.DoubleValue PARRY_BASE_MIN_PERCENT;
    public static final ForgeConfigSpec.DoubleValue POSTURE_BREAK_RESISTANCE_STEP;
    public static final ForgeConfigSpec.DoubleValue POSTURE_BREAK_RESISTANCE_MAX;
    public static final ForgeConfigSpec.DoubleValue REGEN_DELAY_SECONDS;
    public static final ForgeConfigSpec.DoubleValue REGEN_RATE_PERCENT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> POSTURE_MULTIPLIERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;
    public static final ForgeConfigSpec.BooleanValue AUTOMATIC_COMPATIBILITY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOD_ALLOWLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOD_DENYLIST;
    public static final ForgeConfigSpec SPEC;

    private static final Snapshot CLIENT_FAIL_CLOSED_DEFAULTS = new Snapshot(
            1.0D,
            (double) MINIMUM_POSTURE,
            USE_ARMOR_SCALING,
            Set.of(),
            false,
            false,
            false,
            CompatibilityFilterMode.DENYLIST,
            Set.of(),
            Set.of()
    );

    private static volatile Set<ResourceLocation> blacklist = Set.of();
    private static volatile Map<ResourceLocation, Float> entityMultipliers = Map.of();
    private static volatile Set<String> modAllowlist = Set.of();
    private static volatile Set<String> modDenylist = Set.of();
    private static volatile Snapshot remoteSnapshot;

    static {
        BUILDER.push("posture");
        MAXIMUM_POSTURE_MULTIPLIER = BUILDER
                .comment(
                        "Multiplier for the maximum Posture of all entities.",
                        "Default: 1.0 | Range: 0.1-100.0"
                )
                .defineInRange("maximum_posture_multiplier", 1.0D, 0.1D, 100.0D);
        POSTURE_DAMAGE_MULTIPLIER = BUILDER
                .comment(
                        "Multiplier for attack Posture damage from final HP lost.",
                        "Impact gives a bounded bonus; 1.0 uses the internal 0.54 attack coefficient.",
                        "Default: 1.0 | Range: 0.0-100.0"
                )
                .defineInRange("posture_damage_multiplier", 1.0D, 0.0D, 100.0D);
        PARRY_BASE_MAX_PERCENT = BUILDER
                .comment(
                        "Maximum base parry Posture damage (% of maximum) for innate EHP <= 20.",
                        "Innate EHP below 20 is clamped to 20 for parry scaling.",
                        "Default: 22.0 | Range: 0.0-100.0"
                )
                .defineInRange("parry_base_max_percent", 22.0D, 0.0D, 100.0D);
        PARRY_BASE_MIN_PERCENT = BUILDER
                .comment(
                        "Minimum base parry Posture damage (%) as innate EHP approaches infinity.",
                        "Independent of current HP; asymptote as innate EHP grows.",
                        "Default: 8.0 | Range: 0.0-100.0"
                )
                .defineInRange("parry_base_min_percent", 8.0D, 0.0D, 100.0D);
        POSTURE_BREAK_RESISTANCE_STEP = BUILDER
                .comment(
                        "Extra required Posture damage multiplier after each successful break.",
                        "0.0 disables scaling; 0.5 means 1x, 1.5x, 2x on successive breaks.",
                        "Default: 0.5 | Range: 0.0-100.0"
                )
                .defineInRange("posture_break_resistance_step", 0.5D, 0.0D, 100.0D);
        POSTURE_BREAK_RESISTANCE_MAX = BUILDER
                .comment(
                        "Cap on required Posture damage multiplier after repeated breaks.",
                        "Applies to direct attacks and parries; first break always needs 1x.",
                        "Default: 2.0 | Range: 1.0-100.0"
                )
                .defineInRange("posture_break_resistance_max", 2.0D, 1.0D, 100.0D);
        BUILDER.pop();

        BUILDER.push("regeneration");
        REGEN_DELAY_SECONDS = BUILDER
                .comment(
                        "Seconds before Posture regeneration starts.",
                        "Default: 15.0 | Range: 0.0-60.0"
                )
                .defineInRange("delay_seconds", 15.0D, 0.0D, 60.0D);
        REGEN_RATE_PERCENT = BUILDER
                .comment(
                        "Percentage of maximum Posture regenerated per second.",
                        "Default: 2.0 | Range: 0.0-100.0"
                )
                .defineInRange("percent_per_second", 2.0D, 0.0D, 100.0D);
        BUILDER.pop();

        BUILDER.push("entities");
        POSTURE_MULTIPLIERS = BUILDER
                .comment(
                        "Per-entity maximum Posture multipliers.",
                        "Overrides maximum_posture_multiplier for the specified entity.",
                        "Default: [] | Multiplier range: 0.01-100.0",
                        "Example: [\"cataclysm:ignis=0.25\"]"
                )
                .defineListAllowEmpty("posture_multipliers", List.of(), value -> value instanceof String);
        ENTITY_BLACKLIST = BUILDER
                .comment(
                        "Entities completely excluded from EFPI.",
                        "Default: []",
                        "Example: [\"minecraft:zombie\"]"
                )
                .defineListAllowEmpty("entity_blacklist", List.of(), value -> value instanceof String);
        BUILDER.pop();

        BUILDER.push("compatibility");
        AUTOMATIC_COMPATIBILITY = BUILDER
                .comment(
                        "Automatically add EFPI compatibility to supported mobs that do not already have an Epic Fight patch.",
                        "false = only mobs already patched by Epic Fight or another mod are supported.",
                        "Default: true"
                )
                .define("automatic_compatibility", true);
        MOD_ALLOWLIST = BUILDER
                .comment(
                        "If non-empty, automatic compatibility is limited to these mod IDs.",
                        "Default: []",
                        "Example: [\"cataclysm\", \"irons_spellbooks\"]"
                )
                .defineListAllowEmpty("mod_allowlist", List.of(), value -> value instanceof String);
        MOD_DENYLIST = BUILDER
                .comment(
                        "Mod IDs excluded from automatic compatibility. Denylist takes priority over allowlist.",
                        "Default: []",
                        "Example: [\"some_mod\"]"
                )
                .defineListAllowEmpty("mod_denylist", List.of(), value -> value instanceof String);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    /** Alias retained for internal callers that only need the global maximum-posture multiplier. */
    public static float globalMultiplier() {
        Snapshot snapshot = effectiveClientSnapshot();
        return (snapshot != null ? snapshot.globalMultiplier() : MAXIMUM_POSTURE_MULTIPLIER.get()).floatValue();
    }

    /** Server-side maximum-posture multiplier with an entity override replacing the global value. */
    public static float maximumPostureMultiplier(ResourceLocation entityId) {
        Float override = entityId == null ? null : entityMultipliers.get(entityId);
        return override != null ? override : MAXIMUM_POSTURE_MULTIPLIER.get().floatValue();
    }

    /** Linear observed-HP-loss coefficient with configurable attack scaling. */
    public static float attackPostureCoefficient() {
        return BASE_ATTACK_POSTURE_COEFFICIENT * POSTURE_DAMAGE_MULTIPLIER.get().floatValue();
    }

    /** Maximum base parry fraction, for innate EHP <= 20 (independent of current HP). */
    public static float parryBaseMaxFraction() {
        return PARRY_BASE_MAX_PERCENT.get().floatValue() / 100.0F;
    }

    /** Minimum base parry fraction, as innate EHP approaches infinity (independent of current HP). */
    public static float parryBaseMinFraction() {
        return PARRY_BASE_MIN_PERCENT.get().floatValue() / 100.0F;
    }

    /** Added resistance per completed posture break, e.g. 1.0 means +100% each time. */
    public static float postureBreakResistanceStep() {
        return POSTURE_BREAK_RESISTANCE_STEP.get().floatValue();
    }

    /** Upper cap for the required posture-damage multiplier. */
    public static float postureBreakResistanceMax() {
        return POSTURE_BREAK_RESISTANCE_MAX.get().floatValue();
    }

    /** Server-side delay before ordinary posture regeneration begins. */
    public static float postureRegenDelaySeconds() {
        return REGEN_DELAY_SECONDS.get().floatValue();
    }

    /** Constant fraction of maximum posture regenerated per second after the delay. */
    public static float postureRegenFractionPerSecond() {
        return REGEN_RATE_PERCENT.get().floatValue() / 100.0F;
    }

    public static float minimumPosture() {
        Snapshot snapshot = effectiveClientSnapshot();
        return snapshot != null ? snapshot.minimumPosture().floatValue() : MINIMUM_POSTURE;
    }

    public static boolean useArmorScaling() {
        Snapshot snapshot = effectiveClientSnapshot();
        return snapshot != null ? snapshot.useArmorScaling() : USE_ARMOR_SCALING;
    }

    public static boolean automaticCompatibilityEnabled() {
        Snapshot snapshot = effectiveClientSnapshot();
        if (snapshot != null) {
            return snapshot.enableVanillaHumanoidCompatibility()
                    && snapshot.enableExtendedHumanoidCompatibility()
                    && snapshot.enableVanillaSpiderCompatibility();
        }
        return AUTOMATIC_COMPATIBILITY.get();
    }

    public static boolean vanillaHumanoidCompatibilityEnabled() {
        return automaticCompatibilityEnabled();
    }

    public static boolean extendedHumanoidCompatibilityEnabled() {
        return automaticCompatibilityEnabled();
    }

    public static boolean vanillaSpiderCompatibilityEnabled() {
        return automaticCompatibilityEnabled();
    }

    /** Namespace filtering applies only to EFPI automatic compatibility, not to existing third-party patches. */
    public static boolean isAutomaticCompatibilityAllowed(ResourceLocation entityId) {
        if (entityId == null || !automaticCompatibilityEnabled()) {
            return false;
        }
        if ("minecraft".equals(entityId.getNamespace())) {
            return true;
        }

        Snapshot snapshot = effectiveClientSnapshot();
        Set<String> allow = snapshot != null ? snapshot.modAllowlist() : modAllowlist;
        Set<String> deny = snapshot != null ? snapshot.modDenylist() : modDenylist;
        String namespace = entityId.getNamespace();

        if (deny.contains(namespace)) {
            return false;
        }
        return allow.isEmpty() || allow.contains(namespace);
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

    /** Treat an EFPI-owned automatic compatibility target as disabled when its current policy is off. */
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

    /** Snapshot of the local authoritative compatibility policy, used for login synchronization. */
    public static Snapshot localSnapshot() {
        boolean automatic = AUTOMATIC_COMPATIBILITY.get();
        return new Snapshot(
                MAXIMUM_POSTURE_MULTIPLIER.get(),
                (double) MINIMUM_POSTURE,
                USE_ARMOR_SCALING,
                blacklist,
                automatic,
                automatic,
                automatic,
                modAllowlist.isEmpty() ? CompatibilityFilterMode.DENYLIST : CompatibilityFilterMode.ALLOWLIST,
                modAllowlist,
                modDenylist
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
            refreshCachedSettings();
        }
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            refreshCachedSettings();
            synchronizeReloadedSettings();
        }
    }

    private static void refreshCachedSettings() {
        blacklist = parseEntityBlacklist();
        entityMultipliers = parseEntityMultipliers();
        modAllowlist = parseModIds(MOD_ALLOWLIST.get(), "compatibility.mod_allowlist");
        modDenylist = parseModIds(MOD_DENYLIST.get(), "compatibility.mod_denylist");
    }

    private static Set<ResourceLocation> parseEntityBlacklist() {
        Set<ResourceLocation> parsed = new HashSet<>();
        for (String rawId : ENTITY_BLACKLIST.get()) {
            ResourceLocation entityId = ResourceLocation.tryParse(rawId == null ? "" : rawId.trim());
            if (entityId == null) {
                LOGGER.warn("Ignoring invalid entity ID '{}' in entities.entity_blacklist", rawId);
                continue;
            }
            parsed.add(entityId);
        }
        return Set.copyOf(parsed);
    }

    private static Map<ResourceLocation, Float> parseEntityMultipliers() {
        Map<ResourceLocation, Float> parsed = new HashMap<>();
        for (String rawEntry : POSTURE_MULTIPLIERS.get()) {
            String entry = rawEntry == null ? "" : rawEntry.trim();
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                LOGGER.warn("Ignoring invalid entity multiplier '{}' in entities.posture_multipliers", rawEntry);
                continue;
            }

            ResourceLocation entityId = ResourceLocation.tryParse(entry.substring(0, separator).trim());
            float multiplier;
            try {
                multiplier = Float.parseFloat(entry.substring(separator + 1).trim());
            } catch (NumberFormatException exception) {
                multiplier = Float.NaN;
            }
            if (entityId == null || !Float.isFinite(multiplier) || multiplier < 0.01F || multiplier > 100.0F) {
                LOGGER.warn("Ignoring invalid entity multiplier '{}' in entities.posture_multipliers", rawEntry);
                continue;
            }
            parsed.put(entityId, multiplier);
        }
        return Map.copyOf(parsed);
    }

    private static Set<String> parseModIds(List<? extends String> rawIds, String configPath) {
        Set<String> parsed = new HashSet<>();
        for (String rawId : rawIds) {
            String modId = rawId == null ? "" : rawId.trim();
            ResourceLocation probe = ResourceLocation.tryParse(modId + ":efpi_probe");
            if (modId.isEmpty()
                    || probe == null
                    || !modId.equals(probe.getNamespace())
                    || !"efpi_probe".equals(probe.getPath())) {
                LOGGER.warn("Ignoring invalid mod ID '{}' in {}", rawId, configPath);
                continue;
            }
            parsed.add(modId);
        }
        return Set.copyOf(parsed);
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
            boolean enableExtendedHumanoidCompatibility,
            boolean enableVanillaSpiderCompatibility,
            CompatibilityFilterMode compatibilityFilterMode,
            Set<String> modAllowlist,
            Set<String> modDenylist) {
        public Snapshot {
            globalMultiplier = globalMultiplier == null ? 1.0D : globalMultiplier;
            minimumPosture = minimumPosture == null ? (double) MINIMUM_POSTURE : minimumPosture;
            entityBlacklist = entityBlacklist == null ? Set.of() : Set.copyOf(entityBlacklist);
            compatibilityFilterMode = compatibilityFilterMode == null
                    ? CompatibilityFilterMode.DENYLIST
                    : compatibilityFilterMode;
            modAllowlist = modAllowlist == null ? Set.of() : Set.copyOf(modAllowlist);
            modDenylist = modDenylist == null ? Set.of() : Set.copyOf(modDenylist);
        }
    }

    private PostureServerConfig() {
    }
}
