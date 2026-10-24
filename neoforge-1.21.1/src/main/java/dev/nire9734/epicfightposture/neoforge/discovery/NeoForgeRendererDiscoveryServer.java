package dev.nire9734.epicfightposture.neoforge.discovery;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.UUID;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoverySessions;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryReportProcessor;
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
    private static final RendererDiscoverySessions SESSIONS = new RendererDiscoverySessions();
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
            SESSIONS.clear(player.getUUID());
        }
    }

    /** Accept only OP-initiated reloads; never accept an unsolicited discovery report. */
    public static synchronized void requestReloadScan(ServerPlayer player) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || player == null
                || !player.hasPermissions(REQUIRED_PERMISSIONS) || player.getServer() == null) {
            return;
        }
        UUID nonce = SESSIONS.issueAfterReload(player.getUUID(), player.getServer().getTickCount());
        if (nonce != null) sendRequest(player, nonce);
    }

    private static void requestScan(ServerPlayer player) {
        sendRequest(player, SESSIONS.issue(player.getUUID()));
    }

    private static void sendRequest(ServerPlayer player, UUID nonce) {
        LOGGER.info("EFPI requested a renderer discovery report from operator {}", player.getGameProfile().getName());
        PacketDistributor.sendToPlayer(player, new NeoForgeRendererScanRequest(nonce));
    }

    public static synchronized void acceptReport(ServerPlayer player, NeoForgeRendererScanReport report) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || player == null
                || !player.hasPermissions(REQUIRED_PERMISSIONS)) return;
        if (!SESSIONS.consume(player.getUUID(), report.nonce())) {
            LOGGER.warn("EFPI ignored unexpected or expired renderer discovery from {}",
                    player.getGameProfile().getName());
            return;
        }
        RendererDiscoveryReportProcessor.Outcome outcome = RendererDiscoveryReportProcessor.process(
                player.getUUID(), report.fingerprint(), report.inspected(),
                report.vanillaHumanoids(), report.extendedHumanoids(), report.spiders(),
                id -> {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                    return type != null && type != EntityType.PLAYER;
                },
                NeoForgeAutoCompatibility::registerTrustedDedicatedTarget,
                () -> NeoForgeExistingPatchRefresh.refresh(player.getServer()));
        RendererDiscoveryShared.ValidationError error = outcome.validationError();
        if (error != RendererDiscoveryShared.ValidationError.NONE) {
            LOGGER.warn("EFPI rejected renderer discovery from {}: {}",
                    player.getGameProfile().getName(), error);
            player.sendSystemMessage(Component.literal(RendererDiscoveryShared.reportErrorNotice()));
            return;
        }

        if (!outcome.accepted()) {
            LOGGER.warn("EFPI ignored renderer discovery from {} because the cache is full",
                    player.getGameProfile().getName());
            player.sendSystemMessage(Component.literal(RendererDiscoveryShared.reportErrorNotice()));
            return;
        }
        RendererDiscoveryRuntime.LiveRefresh refresh = outcome.refresh();
        LOGGER.info("{}", outcome.acceptanceSummary(player.getGameProfile().getName()));
        String notice = outcome.operatorNotice();
        if (notice != null) {
            player.sendSystemMessage(Component.literal(notice));
        }
    }

    private NeoForgeRendererDiscoveryServer() {}
}
