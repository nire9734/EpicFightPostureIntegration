package dev.nire9734.epicfightposture.neoforge;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.PostureConfigEntries;
import dev.nire9734.epicfightposture.PostureConfigPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.util.thread.EffectiveSide;
import org.slf4j.Logger;

/**
 * Authoritative server settings used by client-side automatic renderer patches.
 * The logical client fails closed until the server sends its policy; the logical
 * server (including integrated servers) always uses its own config.
 */
public final class NeoForgeServerSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    public record Snapshot(
            double globalMultiplier,
            double minimumPosture,
            boolean useArmorScaling,
            boolean vanillaHumanoid,
            boolean extendedHumanoid,
            boolean vanillaSpider,
            Set<ResourceLocation> entityBlacklist,
            Set<String> modAllowlist,
            Set<String> modDenylist) {
        public Snapshot {
            if (!Double.isFinite(globalMultiplier) || globalMultiplier <= 0.0D) globalMultiplier = 1.0D;
            if (!Double.isFinite(minimumPosture) || minimumPosture <= 0.0D) minimumPosture = 1.0D;
            entityBlacklist = entityBlacklist == null ? Set.of() : Set.copyOf(entityBlacklist);
            modAllowlist = modAllowlist == null ? Set.of() : Set.copyOf(modAllowlist);
            modDenylist = modDenylist == null ? Set.of() : Set.copyOf(modDenylist);
        }

        public boolean allows(ResourceLocation id) {
            return id != null && !entityBlacklist.contains(id)
                    && PostureConfigPolicy.allowsAutomatic(
                            vanillaHumanoid && extendedHumanoid && vanillaSpider,
                            id.getNamespace(), modAllowlist, modDenylist);
        }

        /** Forge only suppresses an existing patch when EFPI itself owns it. */
        public boolean blocksPosture(ResourceLocation id, boolean efpiOwned) {
            return PostureConfigPolicy.blocksPosture(id, entityBlacklist, efpiOwned, allows(id));
        }
    }

    private record LocalSettings(Snapshot snapshot,
                                 Map<ResourceLocation, Float> attackDamageMultipliers,
                                 Map<ResourceLocation, Float> parryDamageMultipliers) {}

    private static final Snapshot CLIENT_FAIL_CLOSED = new Snapshot(
            1.0D, 1.0D, true, false, false, false, Set.of(), Set.of(), Set.of());
    private static volatile LocalSettings local;
    private static volatile boolean localConfigReady;
    private static volatile Snapshot remote;

    public static synchronized void invalidateLocal() {
        local = null;
        localConfigReady = true;
    }

    public static Snapshot localSnapshot() {
        return localSettings().snapshot();
    }

    /** Server-only attack damage multiplier; an entity override replaces the global one. */
    public static float attackPostureDamageMultiplier(ResourceLocation id) {
        LocalSettings settings = localSettings();
        return PostureConfigEntries.multiplierFor(id, settings.attackDamageMultipliers(),
                NeoForgePostureConfig.ATTACK_POSTURE_DAMAGE_MULTIPLIER.get().floatValue());
    }

    /** Server-only parry damage multiplier; an entity override replaces the global one. */
    public static float parryPostureDamageMultiplier(ResourceLocation id) {
        LocalSettings settings = localSettings();
        return PostureConfigEntries.multiplierFor(id, settings.parryDamageMultipliers(),
                NeoForgePostureConfig.PARRY_POSTURE_DAMAGE_MULTIPLIER.get().floatValue());
    }

    private static LocalSettings localSettings() {
        LocalSettings result = local;
        if (result == null) {
            synchronized (NeoForgeServerSettings.class) {
                result = local;
                if (result == null) {
                    boolean automatic = NeoForgePostureConfig.AUTOMATIC_COMPATIBILITY.get();
                    Snapshot snapshot = new Snapshot(
                            1.0D, // Legacy network field; maximum Posture is fixed at multiplier 1.0.
                            1.0D, true, automatic, automatic, automatic,
                            PostureConfigEntries.entityBlacklist(NeoForgePostureConfig.ENTITY_BLACKLIST.get(),
                                    ResourceLocation::tryParse,
                                    raw -> LOGGER.warn("Ignoring invalid entity ID '{}' in entities.entity_blacklist", raw)),
                            parseNamespaces(NeoForgePostureConfig.MOD_ALLOWLIST.get(), "compatibility.mod_allowlist"),
                            parseNamespaces(NeoForgePostureConfig.MOD_DENYLIST.get(), "compatibility.mod_denylist"));
                    Map<ResourceLocation, Float> attackOverrides = PostureConfigEntries.entityMultipliers(
                            NeoForgePostureConfig.ATTACK_POSTURE_DAMAGE_MULTIPLIERS.get(), ResourceLocation::tryParse,
                            raw -> LOGGER.warn("Ignoring invalid entity multiplier '{}' in entities.attack_posture_damage_multipliers", raw));
                    Map<ResourceLocation, Float> parryOverrides = PostureConfigEntries.entityMultipliers(
                            NeoForgePostureConfig.PARRY_POSTURE_DAMAGE_MULTIPLIERS.get(), ResourceLocation::tryParse,
                            raw -> LOGGER.warn("Ignoring invalid entity multiplier '{}' in entities.parry_posture_damage_multipliers", raw));
                    result = new LocalSettings(snapshot, attackOverrides, parryOverrides);
                    local = result;
                }
            }
        }
        return result;
    }

    public static Snapshot effectiveSnapshot() {
        if (FMLEnvironment.dist == Dist.CLIENT && EffectiveSide.get() == LogicalSide.CLIENT) {
            Snapshot fromServer = remote;
            return fromServer != null ? fromServer : CLIENT_FAIL_CLOSED;
        }
        return localSnapshot();
    }

    public static boolean allows(ResourceLocation id) {
        // The remote policy is authoritative but must not undo local safety exclusions.
        return isLocalAutomaticCompatibilityEnabled()
                && !isLocalSafetyExcluded(id) && effectiveSnapshot().allows(id);
    }

    /** A local OFF is an opt-out even if the remote server enables automation. */
    public static boolean isLocalAutomaticCompatibilityEnabled() {
        if (!localConfigReady) return false;
        try {
            return NeoForgePostureConfig.AUTOMATIC_COMPATIBILITY.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    /** Read the single COMMON blacklist locally, before the server can synchronize policy. */
    public static boolean isLocalSafetyExcluded(ResourceLocation id) {
        return FMLEnvironment.dist == Dist.CLIENT && id != null
                && localSnapshot().entityBlacklist().contains(id);
    }

    public static boolean hasRegistryCandidates() {
        return isLocalAutomaticCompatibilityEnabled();
    }

    /** Prepare a client-side superset only until a server policy is known. */
    public static boolean canRegister(ResourceLocation id) {
        if (id == null || !isLocalAutomaticCompatibilityEnabled() || isLocalSafetyExcluded(id)) return false;
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Snapshot serverPolicy = remote;
            return serverPolicy == null || serverPolicy.allows(id);
        }
        return localSnapshot().allows(id);
    }

    public static void applyRemoteSnapshot(Snapshot snapshot) {
        if (FMLEnvironment.dist == Dist.CLIENT) remote = snapshot;
    }

    public static void clearRemoteSnapshot() {
        if (FMLEnvironment.dist == Dist.CLIENT) remote = null;
    }

    private static Set<String> parseNamespaces(List<? extends String> entries, String configPath) {
        Set<String> parsed = new HashSet<>();
        for (String entry : entries) {
            String id = entry == null ? "" : entry.trim();
            ResourceLocation check = ResourceLocation.tryParse(id + ":efpi_probe");
            if (id.isEmpty() || id.length() > 128 || check == null || !check.getNamespace().equals(id)
                    || !"efpi_probe".equals(check.getPath())) {
                LOGGER.warn("Ignoring invalid mod ID '{}' in {}", entry, configPath);
                continue;
            }
            parsed.add(id);
        }
        return Set.copyOf(parsed);
    }

    private NeoForgeServerSettings() {}
}
