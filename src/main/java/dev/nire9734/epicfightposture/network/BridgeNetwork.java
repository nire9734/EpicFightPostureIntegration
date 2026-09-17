package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BridgeNetwork {
    private static final String VERSION = "1";
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

        channel.messageBuilder(PostureSyncPacket.class, 0)
                .encoder(PostureSyncPacket::encode)
                .decoder(PostureSyncPacket::decode)
                .consumerMainThread(PostureSyncPacket::handle)
                .add();
    }

    public static void sendTracking(Entity entity, PostureSyncPacket packet) {
        if (channel != null && entity != null) {
            channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
        }
    }

    public static void sendPlayer(ServerPlayer player, PostureSyncPacket packet) {
        if (channel != null && player != null) {
            channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    private BridgeNetwork() {}
}
