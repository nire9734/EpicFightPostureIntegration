package dev.nire9734.epicfightposture.compat.humanoid;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.network.BridgeNetwork;
import dev.nire9734.epicfightposture.network.HumanoidDiscoveryRequestPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dedicated-server handshake for trusted OP renderer-discovery reports. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanoidDiscoveryServer {
    private static final int REQUIRED_PERMISSION_LEVEL = 2;
    private static final Map<UUID, UUID> PENDING_NONCES = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER
                || !(event.getEntity() instanceof ServerPlayer player)
                || !player.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
            return;
        }
        requestScan(player);
    }

    @SubscribeEvent
    public static synchronized void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_NONCES.remove(player.getUUID());
        }
    }

    private static synchronized void requestScan(ServerPlayer player) {
        UUID nonce = UUID.randomUUID();
        PENDING_NONCES.put(player.getUUID(), nonce);
        BridgeNetwork.sendPlayer(player, new HumanoidDiscoveryRequestPacket(nonce));
    }

    public static synchronized void handleUpload(
            ServerPlayer sender,
            UUID nonce,
            String environmentFingerprint,
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> geckoLibHumanoid) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER
                || sender == null
                || !sender.hasPermissions(REQUIRED_PERMISSION_LEVEL)) {
            return;
        }

        UUID expectedNonce = PENDING_NONCES.remove(sender.getUUID());
        if (expectedNonce == null || !expectedNonce.equals(nonce)) {
            return;
        }

        if (environmentFingerprint == null || !environmentFingerprint.matches("[0-9a-f]{64}")) {
            sender.sendSystemMessage(Component.literal("EFPI: rejected invalid Humanoid discovery fingerprint."));
            return;
        }
        if (!inspected.containsAll(vanillaHumanoid) || !inspected.containsAll(geckoLibHumanoid)) {
            sender.sendSystemMessage(Component.literal("EFPI: rejected inconsistent Humanoid discovery report."));
            return;
        }
        Set<ResourceLocation> overlap = new HashSet<>(vanillaHumanoid);
        overlap.retainAll(geckoLibHumanoid);
        if (!overlap.isEmpty()) {
            sender.sendSystemMessage(Component.literal("EFPI: rejected ambiguous Humanoid discovery report."));
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
        Set<ResourceLocation> validGecko = new HashSet<>(geckoLibHumanoid);
        validGecko.retainAll(validInspected);

        HumanoidServerDiscoveryStore.UpdateResult result = HumanoidServerDiscoveryStore.acceptReport(
                environmentFingerprint,
                validInspected,
                validVanilla,
                validGecko
        );

        if (!result.accepted()) {
            sender.sendSystemMessage(Component.literal("EFPI: Humanoid discovery report cache is full; report ignored."));
            return;
        }

        String summary = "EFPI: Humanoid discovery: "
                + result.pendingCount() + " confirmed ("
                + result.pendingVanillaCount() + " vanilla, "
                + result.pendingGeckoCount() + " GeckoLib), "
                + result.conflictCount() + " conflict(s).";
        if (result.restartRequired()) {
            summary += " Restart the server to apply the pending compatibility list.";
        } else {
            summary += " No restart is required.";
        }
        sender.sendSystemMessage(Component.literal(summary));
    }

    private HumanoidDiscoveryServer() {
    }
}
