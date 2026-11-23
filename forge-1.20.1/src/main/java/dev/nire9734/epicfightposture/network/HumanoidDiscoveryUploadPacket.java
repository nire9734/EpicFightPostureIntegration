package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.compat.humanoid.HumanoidDiscoveryServer;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryUpload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One chunk of an OP-client renderer classification report sent to a
 * dedicated server. Large reports span several chunks sharing the nonce so
 * each packet stays under Minecraft's 32767-byte serverbound payload limit.
 */
public final class HumanoidDiscoveryUploadPacket {
    private static final int FINGERPRINT_LENGTH = 64;

    private final UUID nonce;
    private final String environmentFingerprint;
    private final RendererDiscoveryUpload.Chunk<ResourceLocation> chunk;

    public HumanoidDiscoveryUploadPacket(UUID nonce, String environmentFingerprint,
                                         RendererDiscoveryUpload.Chunk<ResourceLocation> chunk) {
        this.nonce = nonce;
        this.environmentFingerprint = environmentFingerprint == null ? "" : environmentFingerprint;
        this.chunk = chunk;
    }

    public static void encode(HumanoidDiscoveryUploadPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.nonce);
        buf.writeUtf(packet.environmentFingerprint, FINGERPRINT_LENGTH);
        buf.writeVarInt(packet.chunk.index());
        buf.writeVarInt(packet.chunk.count());
        writeIds(buf, packet.chunk.inspected());
        writeIds(buf, packet.chunk.vanillaHumanoid());
        writeIds(buf, packet.chunk.extendedHumanoid());
        writeIds(buf, packet.chunk.vanillaSpider());
    }

    public static HumanoidDiscoveryUploadPacket decode(FriendlyByteBuf buf) {
        UUID nonce = buf.readUUID();
        String fingerprint = buf.readUtf(FINGERPRINT_LENGTH);
        int index = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 1 || count > RendererDiscoveryUpload.MAX_CHUNKS || index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid renderer discovery chunk " + index + "/" + count);
        }
        return new HumanoidDiscoveryUploadPacket(nonce, fingerprint, new RendererDiscoveryUpload.Chunk<>(
                index, count, readIds(buf), readIds(buf), readIds(buf), readIds(buf)));
    }

    /** Registered with consumerMainThread, so this already runs on the server thread. */
    public static void handle(
            HumanoidDiscoveryUploadPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        HumanoidDiscoveryServer.handleUpload(
                context.getSender(), packet.nonce, packet.environmentFingerprint, packet.chunk);
        context.setPacketHandled(true);
    }

    private static void writeIds(FriendlyByteBuf buf, List<ResourceLocation> ids) {
        if (ids.size() > RendererDiscoveryUpload.MAX_IDS_PER_CATEGORY) {
            throw new IllegalArgumentException("Too many entity IDs in renderer discovery packet");
        }
        buf.writeVarInt(ids.size());
        ids.forEach(buf::writeResourceLocation);
    }

    private static List<ResourceLocation> readIds(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > RendererDiscoveryUpload.MAX_IDS_PER_CATEGORY) {
            throw new IllegalArgumentException("Invalid renderer discovery entity count: " + count);
        }
        List<ResourceLocation> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readResourceLocation());
        }
        return ids;
    }
}
