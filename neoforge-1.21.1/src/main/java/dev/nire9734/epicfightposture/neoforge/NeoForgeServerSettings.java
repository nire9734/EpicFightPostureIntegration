package dev.nire9734.epicfightposture.neoforge;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.util.thread.EffectiveSide;

/**
 * Authoritative server settings used by client-side automatic renderer patches.
 * The logical client fails closed until the server sends its policy; the logical
 * server (including integrated servers) always uses its own config.
 */
public final class NeoForgeServerSettings {
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
    }

    private static final Snapshot CLIENT_FAIL_CLOSED = new Snapshot(
            1.0D, 1.0D, true, false, false, false, Set.of(), Set.of(), Set.of());
    private static volatile Snapshot local;
    private static volatile Snapshot remote;

    public static void invalidateLocal() {
        local = null;
    }

    public static Snapshot localSnapshot() {
        Snapshot result = local;
        if (result == null) {
            result = new Snapshot(
                    NeoForgePostureConfig.MAXIMUM_POSTURE_MULTIPLIER.get(),
                    1.0D,
                    true,
                    NeoForgePostureConfig.AUTOMATIC_COMPATIBILITY.get(),
                    NeoForgePostureConfig.AUTOMATIC_COMPATIBILITY.get(),
                    NeoForgePostureConfig.AUTOMATIC_COMPATIBILITY.get(),
                    parseEntities(NeoForgePostureConfig.ENTITY_BLACKLIST.get()),
                    parseNamespaces(NeoForgePostureConfig.MOD_ALLOWLIST.get()),
                    parseNamespaces(NeoForgePostureConfig.MOD_DENYLIST.get()));
            local = result;
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

    private static Set<ResourceLocation> parseEntities(List<? extends String> entries) {
        Set<ResourceLocation> parsed = new HashSet<>();
        for (String entry : entries) {
            ResourceLocation id = ResourceLocation.tryParse(entry == null ? "" : entry.trim());
            if (id != null) parsed.add(id);
        }
        return Set.copyOf(parsed);
    }

    private static Set<String> parseNamespaces(List<? extends String> entries) {
        Set<String> parsed = new HashSet<>();
        for (String entry : entries) {
            String id = entry == null ? "" : entry.trim();
            if (id.isEmpty() || id.length() > 128) continue;
            ResourceLocation check = ResourceLocation.tryParse(id + ":efpi_probe");
            if (check != null && check.getNamespace().equals(id)) parsed.add(id);
        }
        return Set.copyOf(parsed);
    }

    private NeoForgeServerSettings() {}
}
