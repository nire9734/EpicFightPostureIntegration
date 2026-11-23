package dev.nire9734.epicfightposture.neoforge.discovery;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.UUID;
import dev.nire9734.epicfightposture.compat.humanoid.OperatorNoticeText;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoverySessions;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryReportProcessor;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryShared;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryStore;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryUpload;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryRuntime;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeExistingPatchRefresh;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureCoverage;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private static final RendererDiscoveryUpload.Assembler<ResourceLocation> UPLOADS =
            new RendererDiscoveryUpload.Assembler<>();
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
            UPLOADS.clear(player.getUUID());
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

    /** Buffers one chunk; the report is processed once every chunk for the nonce has arrived. */
    public static synchronized void acceptReport(ServerPlayer player, NeoForgeRendererScanReport packet) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || player == null
                || !player.hasPermissions(REQUIRED_PERMISSIONS)) return;
        // Only a nonce this server issued may buffer data.
        if (!SESSIONS.isPending(player.getUUID(), packet.nonce())) {
            LOGGER.warn("EFPI ignored unexpected or expired renderer discovery from {}",
                    player.getGameProfile().getName());
            return;
        }
        RendererDiscoveryUpload.Chunk<ResourceLocation> chunk = packet.chunk();
        RendererDiscoveryUpload.Result<ResourceLocation> upload = UPLOADS.accept(
                player.getUUID(), packet.nonce(), packet.fingerprint(), chunk.index(), chunk.count(),
                chunk.inspected(), chunk.vanillaHumanoid(), chunk.extendedHumanoid(), chunk.vanillaSpider());
        if (upload.status() == RendererDiscoveryUpload.Status.INCOMPLETE) return;
        SESSIONS.consume(player.getUUID(), packet.nonce());
        if (upload.status() == RendererDiscoveryUpload.Status.REJECTED) {
            LOGGER.warn("EFPI rejected renderer discovery from {}: {}",
                    player.getGameProfile().getName(), upload.rejection());
            player.sendSystemMessage(OperatorNoticeText.failure());
            return;
        }
        RendererDiscoveryUpload.Report<ResourceLocation> report = upload.report();
        RendererDiscoveryReportProcessor.Outcome outcome = RendererDiscoveryReportProcessor.process(
                player.getUUID(), report.fingerprint(), report.inspected(),
                report.vanillaHumanoid(), report.extendedHumanoid(), report.vanillaSpider(),
                id -> {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                    return type != null && type != EntityType.PLAYER;
                },
                NeoForgeAutoCompatibility::registerTrustedDedicatedTarget,
                affected -> NeoForgeExistingPatchRefresh.refresh(player.getServer(), affected));
        RendererDiscoveryShared.ValidationError error = outcome.validationError();
        if (error != RendererDiscoveryShared.ValidationError.NONE) {
            LOGGER.warn("EFPI rejected renderer discovery from {}: {}",
                    player.getGameProfile().getName(), error);
            player.sendSystemMessage(OperatorNoticeText.failure());
            return;
        }

        if (!outcome.accepted()) {
            LOGGER.warn("EFPI ignored renderer discovery from {} because the cache is full",
                    player.getGameProfile().getName());
            player.sendSystemMessage(OperatorNoticeText.failure());
            return;
        }
        RendererDiscoveryRuntime.LiveRefresh refresh = outcome.refresh();
        NeoForgePostureCoverage.replaceTrustedRendererScan(
                RendererDiscoveryReportProcessor.diagnosticSnapshot(report.inspected()));
        LOGGER.info("{}", outcome.acceptanceSummary(player.getGameProfile().getName()));
        RendererDiscoveryShared.OperatorNotice notice = outcome.operatorNotice();
        if (notice != null) {
            player.sendSystemMessage(OperatorNoticeText.of(notice));
        }
    }

    private NeoForgeRendererDiscoveryServer() {}
}
