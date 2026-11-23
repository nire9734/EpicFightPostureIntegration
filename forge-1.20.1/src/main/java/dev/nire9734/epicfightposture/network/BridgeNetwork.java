package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.NetworkProtocol;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BridgeNetwork {
    // Shared protocol identifier ("mod 3.3.0 or later"); Forge's mismatch
    // screen shows the installed mod versions on both sides instead.
    private static final String VERSION = NetworkProtocol.VERSION;
    private static SimpleChannel channel;
    private static boolean initialized;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(EpicFightPostureIntegration.MODID, "main"),
                () -> VERSION,
                VERSION::equals,
                VERSION::equals
        );

        channel.messageBuilder(PostureSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PostureSyncPacket::encode)
                .decoder(PostureSyncPacket::decode)
                .consumerMainThread(PostureSyncPacket::handle)
                .add();

        channel.messageBuilder(HumanoidDiscoveryRequestPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HumanoidDiscoveryRequestPacket::encode)
                .decoder(HumanoidDiscoveryRequestPacket::decode)
                .consumerMainThread(HumanoidDiscoveryRequestPacket::handle)
                .add();

        channel.messageBuilder(HumanoidDiscoveryUploadPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HumanoidDiscoveryUploadPacket::encode)
                .decoder(HumanoidDiscoveryUploadPacket::decode)
                .consumerMainThread(HumanoidDiscoveryUploadPacket::handle)
                .add();

        channel.messageBuilder(ServerSettingsSyncPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerSettingsSyncPacket::encode)
                .decoder(ServerSettingsSyncPacket::decode)
                .consumerMainThread(ServerSettingsSyncPacket::handle)
                .add();

        channel.messageBuilder(HumanoidDiscoveryRefreshPacket.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HumanoidDiscoveryRefreshPacket::encode)
                .decoder(HumanoidDiscoveryRefreshPacket::decode)
                .consumerMainThread(HumanoidDiscoveryRefreshPacket::handle)
                .add();

        channel.messageBuilder(ServerVersionPacket.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerVersionPacket::encode)
                .decoder(ServerVersionPacket::decode)
                .consumerMainThread(ServerVersionPacket::handle)
                .add();
    }

    public static void sendTracking(Entity entity, PostureSyncPacket packet) {
        if (channel != null && entity != null) {
            channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
        }
    }

    public static void sendPlayer(ServerPlayer player, Object packet) {
        if (channel != null && player != null && packet != null) {
            channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public static void sendToServer(Object packet) {
        if (channel != null && packet != null) {
            channel.sendToServer(packet);
        }
    }

    private BridgeNetwork() {}
}
