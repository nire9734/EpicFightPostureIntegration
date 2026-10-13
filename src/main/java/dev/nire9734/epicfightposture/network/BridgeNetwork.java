package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BridgeNetwork {
    // Public 1.1.0 used protocol 1. The unreleased 2.0.0 contract remains protocol 2,
    // including renderer discovery and server-settings synchronization packets.
    private static final String VERSION = "2";
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
