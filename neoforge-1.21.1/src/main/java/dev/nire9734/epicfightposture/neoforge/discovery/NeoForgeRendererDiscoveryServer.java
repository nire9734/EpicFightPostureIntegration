package dev.nire9734.epicfightposture.neoforge.discovery;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryShared;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryStore;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryRuntime;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeExistingPatchRefresh;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Trusted operator report ingestion and live activation for future spawns
 * and for compatible entities already loaded in the current server process.
 */
public final class NeoForgeRendererDiscoveryServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int REQUIRED_PERMISSIONS = 2;
    private static final Map<UUID, UUID> PENDING_NONCES = new HashMap<>();
    private static final Map<UUID, Integer> LAST_RELOAD_REQUEST_TICK = new HashMap<>();
    private static volatile Set<ResourceLocation> active = Set.of();
    private static volatile Set<ResourceLocation> extended = Set.of();
    private static volatile Set<ResourceLocation> spiders = Set.of();
    private static boolean registered;

    public static synchronized void bootstrap() {
        if (registered) return;
        registered = true;
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) return;
        RendererDiscoveryStore.ActiveTargets approved =
                RendererDiscoveryStore.bootstrap(NeoForgeEnvironmentFingerprint.current(),
                        FMLPaths.CONFIGDIR.get());
        active = approved.vanillaHumanoid();
        extended = approved.extendedHumanoid();
        spiders = approved.vanillaSpider();
        NeoForge.EVENT_BUS.addListener(NeoForgeRendererDiscoveryServer::onLogin);
        NeoForge.EVENT_BUS.addListener(NeoForgeRendererDiscoveryServer::onPermissionsChanged);
        NeoForge.EVENT_BUS.addListener(NeoForgeRendererDiscoveryServer::onLogout);
    }

    /** Immutable approved ids from the previous dedicated-server process. */
    public static Set<ResourceLocation> activeHumanoids() { return active; }
    public static Set<ResourceLocation> activeExtended() { return extended; }
    public static Set<ResourceLocation> activeSpiders() { return spiders; }

    private static synchronized void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER
                || !(event.getEntity() instanceof ServerPlayer player)
                || !player.hasPermissions(REQUIRED_PERMISSIONS)) return;
        requestScan(player);
    }

    /** Trigger discovery for a player made OP after joining, without a reconnect. */
    private static synchronized void onPermissionsChanged(PermissionsChangedEvent event) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER
                || event.isCanceled()
                || event.getOldLevel() >= REQUIRED_PERMISSIONS
                || event.getNewLevel() < REQUIRED_PERMISSIONS
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        requestScan(player);
    }

    private static synchronized void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_NONCES.remove(player.getUUID());
            LAST_RELOAD_REQUEST_TICK.remove(player.getUUID());
        }
    }

    /** Accept only OP-initiated reloads; never accept an unsolicited discovery report. */
    public static synchronized void requestReloadScan(ServerPlayer player) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || player == null
                || !player.hasPermissions(REQUIRED_PERMISSIONS) || player.getServer() == null) {
            return;
        }
        int tick = player.getServer().getTickCount();
        Integer last = LAST_RELOAD_REQUEST_TICK.get(player.getUUID());
        if (last != null && tick >= last
                && tick - last < RendererDiscoveryShared.RELOAD_REQUEST_COOLDOWN_TICKS) {
            return;
        }
        LAST_RELOAD_REQUEST_TICK.put(player.getUUID(), tick);
        requestScan(player);
    }

    private static void requestScan(ServerPlayer player) {
        UUID nonce = UUID.randomUUID();
        PENDING_NONCES.put(player.getUUID(), nonce);
        PacketDistributor.sendToPlayer(player, new NeoForgeRendererScanRequest(nonce));
    }

    public static synchronized void acceptReport(ServerPlayer player, NeoForgeRendererScanReport report) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || player == null
                || !player.hasPermissions(REQUIRED_PERMISSIONS)) return;
        UUID expected = PENDING_NONCES.remove(player.getUUID());
        if (expected == null || !expected.equals(report.nonce())) {
            LOGGER.warn("EFPI ignored unexpected or expired renderer discovery from {}",
                    player.getGameProfile().getName());
            return;
        }
        RendererDiscoveryShared.ValidationError error = RendererDiscoveryShared.validateReport(
                report.fingerprint(), report.inspected(), report.vanillaHumanoids(),
                report.extendedHumanoids(), report.spiders());
        if (error != RendererDiscoveryShared.ValidationError.NONE) {
            LOGGER.warn("EFPI rejected renderer discovery from {}: {}",
                    player.getGameProfile().getName(), error);
            player.sendSystemMessage(Component.literal(RendererDiscoveryShared.reportErrorNotice()));
            return;
        }

        Set<ResourceLocation> inspected = new HashSet<>();
        for (ResourceLocation id : report.inspected()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type != null && type != EntityType.PLAYER) inspected.add(id);
        }
        Set<ResourceLocation> vanilla = new HashSet<>(report.vanillaHumanoids());
        Set<ResourceLocation> extended = new HashSet<>(report.extendedHumanoids());
        Set<ResourceLocation> spider = new HashSet<>(report.spiders());
        vanilla.retainAll(inspected);
        extended.retainAll(inspected);
        spider.retainAll(inspected);
        RendererDiscoveryStore.ActiveTargets previous = RendererDiscoveryStore.pendingTargets();
        Set<ResourceLocation> previousConflicts = RendererDiscoveryStore.conflicts();
        RendererDiscoveryStore.UpdateResult result =
                RendererDiscoveryStore.acceptOperatorReport(
                        player.getUUID(), report.fingerprint(), inspected, vanilla, extended, spider);
        if (!result.accepted()) {
            LOGGER.warn("EFPI ignored renderer discovery from {} because the cache is full",
                    player.getGameProfile().getName());
            player.sendSystemMessage(Component.literal(RendererDiscoveryShared.reportErrorNotice()));
            return;
        }
        RendererDiscoveryStore.ActiveTargets current = RendererDiscoveryStore.pendingTargets();
        RendererDiscoveryRuntime.ActivationUpdate activation = RendererDiscoveryRuntime.activateTrackingChanges(
                previous, current, NeoForgeAutoCompatibility::registerTrustedDedicatedTarget);
        RendererDiscoveryRuntime.LiveRefresh refresh = NeoForgeExistingPatchRefresh.refresh(player.getServer());
        LOGGER.info("EFPI accepted renderer discovery from {}: {} vanilla, {} extended, {} spiders, "
                        + "{} conflicts; {} runtime types ready, {} skipped; {} existing mobs updated, {} failed",
                player.getGameProfile().getName(), result.pendingVanillaCount(),
                result.pendingExtendedCount(), result.pendingSpiderCount(), result.conflictCount(),
                activation.ready(), activation.skipped(), refresh.updated(), refresh.failed());
        Set<ResourceLocation> newConflicts = new HashSet<>(RendererDiscoveryStore.conflicts());
        newConflicts.removeAll(previousConflicts);
        String notice = RendererDiscoveryShared.changeNotice(
                activation.newlyReady(),
                RendererDiscoveryRuntime.withdrawnTargets(previous, current),
                refresh.updated(), newConflicts.size(), activation.newlySkipped(), refresh.failed());
        if (notice != null) {
            player.sendSystemMessage(Component.literal(notice));
        }
    }

    private NeoForgeRendererDiscoveryServer() {}
}
