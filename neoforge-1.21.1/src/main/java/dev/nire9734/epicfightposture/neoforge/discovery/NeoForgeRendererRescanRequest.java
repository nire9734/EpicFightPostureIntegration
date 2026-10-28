package dev.nire9734.epicfightposture.neoforge.discovery;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Empty client request after resource reload; the server decides whether to scan. */
public record NeoForgeRendererRescanRequest() implements CustomPacketPayload {
    public static final Type<NeoForgeRendererRescanRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("epicfight_posture_integration", "renderer_rescan_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NeoForgeRendererRescanRequest> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> { }, buffer -> new NeoForgeRendererRescanRequest());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
