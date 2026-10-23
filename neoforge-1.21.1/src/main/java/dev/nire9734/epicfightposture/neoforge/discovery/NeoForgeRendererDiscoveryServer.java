package dev.nire9734.epicfightposture.neoforge.discovery;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryShared;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Trusted operator report ingestion. No dynamic patch registration occurs in
 * response to a packet: promotion is delayed until the next server restart.
 */
public final class NeoForgeRendererDiscoveryServer {
    private static final int REQUIRED_PERMISSIONS = 2;
    private static final Map<UUID, UUID> PENDING_NONCES = new HashMap<>();
    private static volatile Set<ResourceLocation> active = Set.of();
    private static volatile Set<ResourceLocation> extended = Set.of();
    private static volatile Set<ResourceLocation> spiders = Set.of();
    private static boolean registered;

    public static synchronized void bootstrap() {
        if (registered) return;
        registered = true;
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) return;
        NeoForgeHumanoidDiscoveryStore.ActiveTargets approved =
                NeoForgeHumanoidDiscoveryStore.bootstrap(NeoForgeEnvironmentFingerprint.current());
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
        UUID nonce = UUID.randomUUID();
        PENDING_NONCES.put(player.getUUID(), nonce);
        PacketDistributor.sendToPlayer(player, new NeoForgeRendererScanRequest(nonce));
    }

    /** Trigger discovery for a player made OP after joining, without a reconnect. */
    private static synchronized void onPermissionsChanged(PermissionsChangedEvent event) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER
                || event.isCanceled()
                || event.getOldLevel() >= REQUIRED_PERMISSIONS
                || event.getNewLevel() < REQUIRED_PERMISSIONS
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        UUID nonce = UUID.randomUUID();
        PENDING_NONCES.put(player.getUUID(), nonce);
        PacketDistributor.sendToPlayer(player, new NeoForgeRendererScanRequest(nonce));
    }

    private static synchronized void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PENDING_NONCES.remove(player.getUUID());
    }

    public static synchronized void acceptReport(ServerPlayer player, NeoForgeRendererScanReport report) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || player == null
                || !player.hasPermissions(REQUIRED_PERMISSIONS)) return;
        UUID expected = PENDING_NONCES.remove(player.getUUID());
        if (expected == null || !expected.equals(report.nonce())) return;
        if (!NeoForgeRendererReportRules.isConsistent(
                report.fingerprint(), report.inspected(), report.vanillaHumanoids(),
                report.extendedHumanoids(), report.spiders())) {
            player.sendSystemMessage(Component.literal("EFPI: rejected invalid renderer discovery report."));
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
        NeoForgeHumanoidDiscoveryStore.UpdateResult result =
                NeoForgeHumanoidDiscoveryStore.acceptReport(
                        report.fingerprint(), inspected, vanilla, extended, spider);
        if (result.restartRequired()) {
            String message = RendererDiscoveryShared.restartRequiredMessage(
                    result.pendingVanillaCount(),
                    result.pendingExtendedCount(),
                    result.pendingSpiderCount(),
                    result.conflictCount());
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private NeoForgeRendererDiscoveryServer() {}
}
