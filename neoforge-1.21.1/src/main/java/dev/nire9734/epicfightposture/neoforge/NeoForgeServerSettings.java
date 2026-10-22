package dev.nire9734.epicfightposture.neoforge;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.PostureConfigEntries;
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
            if (id == null || entityBlacklist.contains(id)
                    || !(vanillaHumanoid && extendedHumanoid && vanillaSpider)) return false;
            if ("minecraft".equals(id.getNamespace())) return true;
            if (modDenylist.contains(id.getNamespace())) return false;
            return modAllowlist.isEmpty() || modAllowlist.contains(id.getNamespace());
        }

        /** Forge only suppresses an existing patch when EFPI itself owns it. */
        public boolean blocksPosture(ResourceLocation id, boolean efpiOwned) {
            return id != null && (entityBlacklist.contains(id) || (efpiOwned && !allows(id)));
        }
    }

    private record LocalSettings(Snapshot snapshot, Map<ResourceLocation, Float> entityMultipliers) {}

    private static final Snapshot CLIENT_FAIL_CLOSED = new Snapshot(
            1.0D, 1.0D, true, false, false, false, Set.of(), Set.of(), Set.of());
    private static volatile LocalSettings local;
    private static volatile Snapshot remote;

    public static synchronized void invalidateLocal() {
        local = null;
    }

    public static Snapshot localSnapshot() {
        return localSettings().snapshot();
    }

    /** Server-only override; an entity's configured value replaces the global one. */
    public static float maximumPostureMultiplier(ResourceLocation id) {
        LocalSettings settings = localSettings();
        Float override = id == null ? null : settings.entityMultipliers().get(id);
        return override != null ? override : (float) settings.snapshot().globalMultiplier();
    }

    private static LocalSettings localSettings() {
        LocalSettings result = local;
        if (result == null) {
            synchronized (NeoForgeServerSettings.class) {
                result = local;
                if (result == null) {
                    boolean automatic = NeoForgePostureConfig.AUTOMATIC_COMPATIBILITY.get();
                    Snapshot snapshot = new Snapshot(
                            NeoForgePostureConfig.MAXIMUM_POSTURE_MULTIPLIER.get(),
                            1.0D, true, automatic, automatic, automatic,
                            PostureConfigEntries.entityBlacklist(NeoForgePostureConfig.ENTITY_BLACKLIST.get(),
                                    raw -> LOGGER.warn("Ignoring invalid entity ID '{}' in entities.entity_blacklist", raw)),
                            parseNamespaces(NeoForgePostureConfig.MOD_ALLOWLIST.get(), "compatibility.mod_allowlist"),
                            parseNamespaces(NeoForgePostureConfig.MOD_DENYLIST.get(), "compatibility.mod_denylist"));
                    Map<ResourceLocation, Float> overrides = PostureConfigEntries.entityMultipliers(
                            NeoForgePostureConfig.POSTURE_MULTIPLIERS.get(),
                            raw -> LOGGER.warn("Ignoring invalid entity multiplier '{}' in entities.posture_multipliers", raw));
                    result = new LocalSettings(snapshot, overrides);
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
        return effectiveSnapshot().allows(id);
    }

    public static boolean hasRegistryCandidates() {
        return FMLEnvironment.dist == Dist.CLIENT || localSnapshot().vanillaHumanoid();
    }

    /** A physical client must register a superset before the server policy arrives. */
    public static boolean canRegister(ResourceLocation id) {
        return id != null && (FMLEnvironment.dist == Dist.CLIENT || localSnapshot().allows(id));
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
