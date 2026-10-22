package dev.nire9734.epicfightposture.neoforge.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Authoritative server-to-client posture snapshot for a tracked mob. */
public record PostureStatePayload(int entityId, UUID uuid, float current,
                                  float max, boolean broken, boolean present)
        implements CustomPacketPayload {
    public static final Type<PostureStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("epicfight_posture_integration", "posture_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PostureStatePayload> STREAM_CODEC =
            StreamCodec.of(PostureStatePayload::encode, PostureStatePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, PostureStatePayload value) {
        buf.writeVarInt(value.entityId());
        buf.writeUUID(value.uuid());
        buf.writeFloat(value.current());
        buf.writeFloat(value.max());
        buf.writeBoolean(value.broken());
        buf.writeBoolean(value.present());
    }

    private static PostureStatePayload decode(RegistryFriendlyByteBuf buf) {
        return new PostureStatePayload(buf.readVarInt(), buf.readUUID(), buf.readFloat(),
                buf.readFloat(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
