package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.client.ClientPostureStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class PostureSyncPacket {
    final int entityId;
    final UUID uuid;
    final float current;
    final float max;
    final boolean broken;
    final boolean present;

    public PostureSyncPacket(int entityId, UUID uuid, float current, float max, boolean broken, boolean present) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.current = current;
        this.max = max;
        this.broken = broken;
        this.present = present;
    }

    public static void encode(PostureSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.entityId);
        UUID id = packet.uuid == null ? new UUID(0L, 0L) : packet.uuid;
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeFloat(packet.current);
        buf.writeFloat(packet.max);
        buf.writeBoolean(packet.broken);
        buf.writeBoolean(packet.present);
    }

    public static PostureSyncPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        UUID uuid = new UUID(buf.readLong(), buf.readLong());
        float current = buf.readFloat();
        float max = buf.readFloat();
        boolean broken = buf.readBoolean();
        boolean present = buf.readBoolean();
        return new PostureSyncPacket(entityId, uuid, current, max, broken, present);
    }

    public static void handle(PostureSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (packet.present) {
                ClientPostureStore.update(packet.entityId, packet.uuid, packet.current, packet.max, packet.broken);
            } else {
                ClientPostureStore.remove(packet.entityId, packet.uuid);
            }
        });
        context.setPacketHandled(true);
    }
}
