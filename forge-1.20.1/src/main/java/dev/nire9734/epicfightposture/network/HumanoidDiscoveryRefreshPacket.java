package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.compat.humanoid.HumanoidDiscoveryServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client renderer resources changed; ask the server to start a fresh OP-only scan. */
public final class HumanoidDiscoveryRefreshPacket {
    public static void encode(HumanoidDiscoveryRefreshPacket packet, FriendlyByteBuf buf) {
        // No client-supplied permission or nonce: only the server may issue scan nonces.
    }

    public static HumanoidDiscoveryRefreshPacket decode(FriendlyByteBuf buf) {
        return new HumanoidDiscoveryRefreshPacket();
    }

    public static void handle(HumanoidDiscoveryRefreshPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> HumanoidDiscoveryServer.requestReloadScan(sender));
        context.setPacketHandled(true);
    }
}
