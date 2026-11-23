package dev.nire9734.epicfightposture.neoforge.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryUpload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One chunk of a bounded, disjoint source-renderer classification report
 * (protocol 4). Large reports span several chunks sharing the nonce so each
 * payload stays under Minecraft's 32767-byte serverbound limit.
 */
public record NeoForgeRendererScanReport(UUID nonce, String fingerprint,
        RendererDiscoveryUpload.Chunk<ResourceLocation> chunk)
        implements CustomPacketPayload {
    public static final Type<NeoForgeRendererScanReport> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("epicfight_posture_integration", "renderer_scan_report"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NeoForgeRendererScanReport> STREAM_CODEC =
            StreamCodec.of(NeoForgeRendererScanReport::encode, NeoForgeRendererScanReport::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, NeoForgeRendererScanReport packet) {
        buffer.writeUUID(packet.nonce());
        buffer.writeUtf(packet.fingerprint(), 64);
        buffer.writeVarInt(packet.chunk().index());
        buffer.writeVarInt(packet.chunk().count());
        writeIds(buffer, packet.chunk().inspected());
        writeIds(buffer, packet.chunk().vanillaHumanoid());
        writeIds(buffer, packet.chunk().extendedHumanoid());
        writeIds(buffer, packet.chunk().vanillaSpider());
    }

    private static NeoForgeRendererScanReport decode(RegistryFriendlyByteBuf buffer) {
        UUID nonce = buffer.readUUID();
        String fingerprint = buffer.readUtf(64);
        int index = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 1 || count > RendererDiscoveryUpload.MAX_CHUNKS || index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid renderer report chunk " + index + "/" + count);
        }
        return new NeoForgeRendererScanReport(nonce, fingerprint, new RendererDiscoveryUpload.Chunk<>(
                index, count, readIds(buffer), readIds(buffer), readIds(buffer), readIds(buffer)));
    }

    private static void writeIds(RegistryFriendlyByteBuf buffer, List<ResourceLocation> ids) {
        if (ids.size() > RendererDiscoveryUpload.MAX_IDS_PER_CATEGORY) {
            throw new IllegalArgumentException("Excessive renderer report size");
        }
        buffer.writeVarInt(ids.size());
        ids.forEach(buffer::writeResourceLocation);
    }

    private static List<ResourceLocation> readIds(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > RendererDiscoveryUpload.MAX_IDS_PER_CATEGORY) {
            throw new IllegalArgumentException("Invalid renderer report count");
        }
        List<ResourceLocation> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) ids.add(buffer.readResourceLocation());
        return ids;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
