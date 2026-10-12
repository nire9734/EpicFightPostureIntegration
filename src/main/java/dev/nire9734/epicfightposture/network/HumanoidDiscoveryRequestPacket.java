package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.compat.humanoid.client.HumanoidClientDiscovery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server-to-OP request for the client's current renderer classification. */
public final class HumanoidDiscoveryRequestPacket {
    private final UUID nonce;

    public HumanoidDiscoveryRequestPacket(UUID nonce) {
        this.nonce = nonce;
    }

    public static void encode(HumanoidDiscoveryRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.nonce);
    }

    public static HumanoidDiscoveryRequestPacket decode(FriendlyByteBuf buf) {
        return new HumanoidDiscoveryRequestPacket(buf.readUUID());
    }

    public static void handle(
            HumanoidDiscoveryRequestPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> HumanoidClientDiscovery.handleServerRequest(packet.nonce)
        ));
        context.setPacketHandled(true);
    }
}
