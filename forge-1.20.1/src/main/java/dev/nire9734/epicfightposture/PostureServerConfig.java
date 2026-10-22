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
     * 3.0.1 uses observed HP depletion with an internal 0.54
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
                        "Changes how much maximum Posture mobs have.",
                        "1.0 = normal, 0.5 = half, 2.0 = double. Higher values make attack-based guard breaks harder.",
                        "Default: 1.0 | Range: 0.1-100.0"
                )
                .defineInRange("maximum_posture_multiplier", 1.0D, 0.1D, 100.0D);
        POSTURE_DAMAGE_MULTIPLIER = BUILDER
                .comment(
                        "Changes how much Posture damage attacks deal (not parries).",
                        "0.0 = no attack Posture damage, 0.5 = half, 1.0 = normal, 2.0 = double.",
                        "Default: 1.0 | Range: 0.0-100.0"
                )
                .defineInRange("posture_damage_multiplier", 1.0D, 0.0D, 100.0D);
        PARRY_BASE_MAX_PERCENT = BUILDER
                .comment(
                        "Posture damage from parries against mobs with low base health and armor.",
                        "22.0 = up to 22% of maximum Posture per parry, before repeated-break resistance.",
                        "The game uses each mob type's original stats, not its current HP or level bonuses.",
                        "Default: 22.0 | Range: 0.0-100.0"
                )
                .defineInRange("parry_base_max_percent", 22.0D, 0.0D, 100.0D);
        PARRY_BASE_MIN_PERCENT = BUILDER
                .comment(
                        "Posture damage from parries against mobs with high base health and armor.",
                        "8.0 = damage approaches 8% of maximum Posture per parry for very tough mobs.",
                        "Actual parry damage varies between these two settings, before repeated-break resistance.",
                        "Default: 8.0 | Range: 0.0-100.0"
                )
                .defineInRange("parry_base_min_percent", 8.0D, 0.0D, 100.0D);
        POSTURE_BREAK_RESISTANCE_STEP = BUILDER
                .comment(
                        "How much harder it becomes to break a mob's Posture after each successful guard break.",
                        "0.5 = normal damage before the first break, then 1.5x and 2x resistance.",
                        "0.0 = no increase. Higher values make repeated guard breaks harder.",
                        "Default: 0.5 | Range: 0.0-100.0"
                )
                .defineInRange("posture_break_resistance_step", 0.5D, 0.0D, 100.0D);
        POSTURE_BREAK_RESISTANCE_MAX = BUILDER
                .comment(
                        "Maximum resistance a mob can build up from repeated guard breaks.",
                        "2.0 = up to twice as much Posture damage is needed for another break.",
                        "This resistance reduces Posture damage from both attacks and parries.",
                        "Default: 2.0 | Range: 1.0-100.0"
                )
                .defineInRange("posture_break_resistance_max", 2.0D, 1.0D, 100.0D);
        BUILDER.pop();

        BUILDER.push("regeneration");
        REGEN_DELAY_SECONDS = BUILDER
                .comment(
                        "Seconds since the last actual Posture reduction before ordinary regeneration starts.",
                        "Both direct attacks and parries restart this timer when they reduce Posture.",
                        "Health damage alone (without Posture loss) does not restart it.",
                        "Does not affect the separate 3-second recovery after a guard break.",
                        "Default: 15.0 | Range: 0.0-60.0"
                )
                .defineInRange("delay_seconds", 15.0D, 0.0D, 60.0D);
        REGEN_RATE_PERCENT = BUILDER
                .comment(
                        "How much maximum Posture a mob regenerates each second after the delay.",
                        "2.0 = 2% per second. 0.0 disables normal regeneration (not guard-break recovery).",
                        "Default: 2.0 | Range: 0.0-100.0"
                )
                .defineInRange("percent_per_second", 2.0D, 0.0D, 100.0D);
        BUILDER.pop();

        BUILDER.push("entities");
        POSTURE_MULTIPLIERS = BUILDER
                .comment(
                        "Override maximum Posture for specific mob types.",
                        "Format: [\"modid:mob_id=multiplier\"]. Replaces the global setting for that mob.",
                        "Default: [] | Multiplier range: 0.01-100.0",
                        "Example: [\"cataclysm:ignis=0.25\"] (Ignis has 25% of normal maximum Posture)"
                )
                .defineListAllowEmpty("posture_multipliers", List.of(), value -> value instanceof String);
        ENTITY_BLACKLIST = BUILDER
                .comment(
                        "Mobs excluded from this mod's Posture system and Posture bar.",
                        "Does not remove existing Epic Fight patches or change the mob's normal behavior.",
                        "Default: []",
                        "Example: [\"minecraft:zombie\"]"
                )
                .defineListAllowEmpty("entity_blacklist", List.of(), value -> value instanceof String);
        BUILDER.pop();

        BUILDER.push("compatibility");
        AUTOMATIC_COMPATIBILITY = BUILDER
                .comment(
                        "Automatically add Posture support to compatible mobs without an Epic Fight patch.",
                        "false = only mobs already supported by Epic Fight or another addon can use Posture.",
                        "Default: true"
                )
                .define("automatic_compatibility", true);
        MOD_ALLOWLIST = BUILDER
                .comment(
                        "Only automatically add Posture support to mobs from these mods.",
                        "[] = allow all mods. Does not restrict mobs with existing Epic Fight patches.",
                        "Vanilla Minecraft mobs are not restricted by this list.",
                        "Default: []",
                        "Example: [\"cataclysm\", \"irons_spellbooks\"]"
                )
                .defineListAllowEmpty("mod_allowlist", List.of(), value -> value instanceof String);
        MOD_DENYLIST = BUILDER
                .comment(
                        "Do not automatically add Posture support to mobs from these mods.",
                        "Overrides mod_allowlist. Existing Epic Fight patches are not disabled.",
                        "Vanilla Minecraft mobs are not restricted by this list.",
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
