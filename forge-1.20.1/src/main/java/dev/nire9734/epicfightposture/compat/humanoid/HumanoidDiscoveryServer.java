package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.network.BridgeNetwork;
import dev.nire9734.epicfightposture.network.HumanoidDiscoveryRequestPacket;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dedicated-server handshake for trusted OP renderer-discovery reports. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanoidDiscoveryServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int REQUIRED_PERMISSION_LEVEL = 2;
    private static final Map<UUID, UUID> PENDING_NONCES = new HashMap<>();
    private static final Map<UUID, Integer> LAST_RELOAD_REQUEST_TICK = new HashMap<>();

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
            PENDING_NONCES.remove(player.getUUID());
            LAST_RELOAD_REQUEST_TICK.remove(player.getUUID());
        }
    }

    /** A client reload is only a request: authentication and the nonce stay server-owned. */
    public static synchronized void requestReloadScan(ServerPlayer player) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || player == null
                || !player.hasPermissions(REQUIRED_PERMISSION_LEVEL) || player.getServer() == null) {
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

    private static synchronized void requestScan(ServerPlayer player) {
        UUID nonce = UUID.randomUUID();
        PENDING_NONCES.put(player.getUUID(), nonce);
        LOGGER.info("EFPI requested a renderer discovery report from operator {}", player.getGameProfile().getName());
        BridgeNetwork.sendPlayer(player, new HumanoidDiscoveryRequestPacket(nonce));
    }

    public static synchronized void handleUpload(
            ServerPlayer sender,
            UUID nonce,
            String environmentFingerprint,
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> extendedHumanoid,
            Set<ResourceLocation> vanillaSpider) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER
                || sender == null
                || !sender.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
            return;
        }

        UUID expectedNonce = PENDING_NONCES.remove(sender.getUUID());
        if (expectedNonce == null || !expectedNonce.equals(nonce)) {
            LOGGER.warn("EFPI ignored an unexpected or expired renderer discovery reply from {}",
                    sender.getGameProfile().getName());
            return;
        }

        RendererDiscoveryShared.ValidationError error = RendererDiscoveryShared.validateReport(
                environmentFingerprint, inspected, vanillaHumanoid, extendedHumanoid, vanillaSpider);
        if (error != RendererDiscoveryShared.ValidationError.NONE) {
            LOGGER.warn("EFPI rejected renderer discovery reply from {}: {}",
                    sender.getGameProfile().getName(), error);
            sender.sendSystemMessage(Component.literal(RendererDiscoveryShared.reportErrorNotice()));
            return;
        }

        Set<ResourceLocation> validInspected = new HashSet<>();
        for (ResourceLocation id : inspected) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (type != null && type != EntityType.PLAYER) {
                validInspected.add(id);
            }
        }

        Set<ResourceLocation> validVanilla = new HashSet<>(vanillaHumanoid);
        validVanilla.retainAll(validInspected);
        Set<ResourceLocation> validExtended = new HashSet<>(extendedHumanoid);
        validExtended.retainAll(validInspected);
        Set<ResourceLocation> validSpider = new HashSet<>(vanillaSpider);
        validSpider.retainAll(validInspected);

        RendererDiscoveryStore.ActiveTargets previous = RendererDiscoveryStore.pendingTargets();
        Set<ResourceLocation> previousConflicts = RendererDiscoveryStore.conflicts();
        RendererDiscoveryStore.UpdateResult result = RendererDiscoveryStore.acceptOperatorReport(
                sender.getUUID(), environmentFingerprint,
                validInspected,
                validVanilla,
                validExtended,
                validSpider
        );

        if (!result.accepted()) {
            LOGGER.warn("EFPI ignored renderer discovery report from {} because the cache is full",
                    sender.getGameProfile().getName());
            sender.sendSystemMessage(Component.literal(RendererDiscoveryShared.reportErrorNotice()));
            return;
        }

        RendererDiscoveryStore.ActiveTargets current = RendererDiscoveryStore.pendingTargets();
        RendererDiscoveryRuntime.ActivationUpdate activation = RendererDiscoveryRuntime.activateTrackingChanges(
                previous, current, VanillaHumanoidCompat::registerTrustedDedicatedTarget);
        LOGGER.info("EFPI accepted renderer discovery from {}: {} vanilla, {} extended, {} spiders, "
                        + "{} conflicts; {} runtime types ready, {} skipped",
                sender.getGameProfile().getName(), result.pendingVanillaCount(),
                result.pendingExtendedCount(), result.pendingSpiderCount(), result.conflictCount(),
                activation.ready(), activation.skipped());
        RendererDiscoveryRuntime.LiveRefresh refresh = ForgeExistingPatchRefresh.refresh(sender.getServer());
        if (refresh.failed() > 0) {
            LOGGER.warn("EFPI could not refresh {} existing mobs after discovery from {}",
                    refresh.failed(), sender.getGameProfile().getName());
        }
        Set<ResourceLocation> newConflicts = new HashSet<>(RendererDiscoveryStore.conflicts());
        newConflicts.removeAll(previousConflicts);
        String notice = RendererDiscoveryShared.changeNotice(
                activation.newlyReady(),
                RendererDiscoveryRuntime.withdrawnTargets(previous, current),
                refresh.updated(), newConflicts.size(), activation.newlySkipped(), refresh.failed());
        if (notice != null) {
            sender.sendSystemMessage(Component.literal(notice));
        }
    }

    private HumanoidDiscoveryServer() {
    }
}
