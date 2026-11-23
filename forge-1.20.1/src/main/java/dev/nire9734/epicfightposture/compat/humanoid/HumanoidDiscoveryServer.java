package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.network.BridgeNetwork;
import dev.nire9734.epicfightposture.network.HumanoidDiscoveryRequestPacket;
import dev.nire9734.epicfightposture.sync.ForgePostureCoverage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PermissionsChangedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;

/** Dedicated-server handshake for trusted OP renderer-discovery reports. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanoidDiscoveryServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int REQUIRED_PERMISSION_LEVEL = 2;
    private static final RendererDiscoverySessions SESSIONS = new RendererDiscoverySessions();
    private static final RendererDiscoveryUpload.Assembler<ResourceLocation> UPLOADS =
            new RendererDiscoveryUpload.Assembler<>();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER
                || !(event.getEntity() instanceof ServerPlayer player)
                || !player.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
            return;
        }
        requestScan(player);
    }

    /**
     * OP is often granted after the first join. Request the renderer scan on
     * permission elevation so the operator need not reconnect.
     */
    @SubscribeEvent
    public static void onPermissionsChanged(PermissionsChangedEvent event) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER
                || event.isCanceled()
                || event.getOldLevel() >= REQUIRED_PERMISSION_LEVEL
                || event.getNewLevel() < REQUIRED_PERMISSION_LEVEL
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        requestScan(player);
    }

    @SubscribeEvent
    public static synchronized void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SESSIONS.clear(player.getUUID());
            UPLOADS.clear(player.getUUID());
        }
    }

    /** A client reload is only a request: authentication and the nonce stay server-owned. */
    public static synchronized void requestReloadScan(ServerPlayer player) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || player == null
                || !player.hasPermissions(REQUIRED_PERMISSION_LEVEL) || player.getServer() == null) {
            return;
        }
        UUID nonce = SESSIONS.issueAfterReload(player.getUUID(), player.getServer().getTickCount());
        if (nonce != null) sendRequest(player, nonce);
    }

    private static synchronized void requestScan(ServerPlayer player) {
        sendRequest(player, SESSIONS.issue(player.getUUID()));
    }

    private static void sendRequest(ServerPlayer player, UUID nonce) {
        LOGGER.info("EFPI requested a renderer discovery report from operator {}", player.getGameProfile().getName());
        BridgeNetwork.sendPlayer(player, new HumanoidDiscoveryRequestPacket(nonce));
    }

    /** Buffers one chunk; the report is processed once every chunk for the nonce has arrived. */
    public static synchronized void handleUpload(
            ServerPlayer sender,
            UUID nonce,
            String environmentFingerprint,
            RendererDiscoveryUpload.Chunk<ResourceLocation> chunk) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER
                || sender == null
                || !sender.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
            return;
        }

        // Only a nonce this server issued may buffer data.
        if (!SESSIONS.isPending(sender.getUUID(), nonce)) {
            LOGGER.warn("EFPI ignored an unexpected or expired renderer discovery reply from {}",
                    sender.getGameProfile().getName());
            return;
        }
        RendererDiscoveryUpload.Result<ResourceLocation> upload = UPLOADS.accept(
                sender.getUUID(), nonce, environmentFingerprint, chunk.index(), chunk.count(),
                chunk.inspected(), chunk.vanillaHumanoid(), chunk.extendedHumanoid(), chunk.vanillaSpider());
        if (upload.status() == RendererDiscoveryUpload.Status.INCOMPLETE) {
            return;
        }
        SESSIONS.consume(sender.getUUID(), nonce);
        if (upload.status() == RendererDiscoveryUpload.Status.REJECTED) {
            LOGGER.warn("EFPI rejected renderer discovery reply from {}: {}",
                    sender.getGameProfile().getName(), upload.rejection());
            sender.sendSystemMessage(Component.literal(RendererDiscoveryShared.reportErrorNotice()));
            return;
        }
        RendererDiscoveryUpload.Report<ResourceLocation> report = upload.report();
        processReport(sender, report.fingerprint(), report.inspected(), report.vanillaHumanoid(),
                report.extendedHumanoid(), report.vanillaSpider());
    }

    private static void processReport(
            ServerPlayer sender,
            String environmentFingerprint,
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> extendedHumanoid,
            Set<ResourceLocation> vanillaSpider) {

        RendererDiscoveryReportProcessor.Outcome outcome = RendererDiscoveryReportProcessor.process(
                sender.getUUID(), environmentFingerprint, inspected,
                vanillaHumanoid, extendedHumanoid, vanillaSpider,
                id -> {
                    EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
                    return type != null && type != EntityType.PLAYER;
                },
                VanillaHumanoidCompat::registerTrustedDedicatedTarget,
                affected -> ForgeExistingPatchRefresh.refresh(sender.getServer(), affected));
        RendererDiscoveryShared.ValidationError error = outcome.validationError();
        if (error != RendererDiscoveryShared.ValidationError.NONE) {
            LOGGER.warn("EFPI rejected renderer discovery reply from {}: {}",
                    sender.getGameProfile().getName(), error);
            sender.sendSystemMessage(Component.literal(RendererDiscoveryShared.reportErrorNotice()));
            return;
        }

        if (!outcome.accepted()) {
            LOGGER.warn("EFPI ignored renderer discovery report from {} because the cache is full",
                    sender.getGameProfile().getName());
            sender.sendSystemMessage(Component.literal(RendererDiscoveryShared.reportErrorNotice()));
            return;
        }
        LOGGER.info("{}", outcome.acceptanceSummary(sender.getGameProfile().getName()));
        ForgePostureCoverage.replaceTrustedRendererScan(
                RendererDiscoveryReportProcessor.diagnosticSnapshot(inspected));
        RendererDiscoveryRuntime.LiveRefresh refresh = outcome.refresh();
        if (refresh.failed() > 0) {
            LOGGER.warn("EFPI could not refresh {} existing mobs after discovery from {}",
                    refresh.failed(), sender.getGameProfile().getName());
        }
        String notice = outcome.operatorNotice();
        if (notice != null) {
            sender.sendSystemMessage(Component.literal(notice));
        }
    }

    private HumanoidDiscoveryServer() {
    }
}
