package dev.nire9734.epicfightposture.neoforge.discovery;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-issued one-time nonce for a permissioned renderer report. */
public record NeoForgeRendererScanRequest(UUID nonce) implements CustomPacketPayload {
    public static final Type<NeoForgeRendererScanRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("epicfight_posture_integration", "renderer_scan_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NeoForgeRendererScanRequest> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> buffer.writeUUID(packet.nonce()),
                    buffer -> new NeoForgeRendererScanRequest(buffer.readUUID()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
