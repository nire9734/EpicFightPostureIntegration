package dev.nire9734.epicfightposture.neoforge.network;

import dev.nire9734.epicfightposture.client.ServerVersionNotice;
import dev.nire9734.epicfightposture.neoforge.EpicFightPostureNeoForge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/** The server's mod version, sent once at login so a client can flag a version mismatch. */
public record NeoForgeServerVersionPayload(String version) implements CustomPacketPayload {
    public static final Type<NeoForgeServerVersionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("epicfight_posture_integration", "server_version"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NeoForgeServerVersionPayload> STREAM_CODEC =
            StreamCodec.of(NeoForgeServerVersionPayload::encode, NeoForgeServerVersionPayload::decode);

    public NeoForgeServerVersionPayload {
        version = version == null ? "" : version;
        if (version.length() > ServerVersionNotice.MAX_VERSION_LENGTH) {
            version = version.substring(0, ServerVersionNotice.MAX_VERSION_LENGTH);
        }
    }

    public static NeoForgeServerVersionPayload current() {
        return new NeoForgeServerVersionPayload(localModVersion());
    }

    /** This installation's mod version, as declared in neoforge.mods.toml. */
    public static String localModVersion() {
        return ModList.get().getModContainerById(EpicFightPostureNeoForge.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    private static void encode(RegistryFriendlyByteBuf buf, NeoForgeServerVersionPayload payload) {
        buf.writeUtf(payload.version(), ServerVersionNotice.MAX_VERSION_LENGTH);
    }

    private static NeoForgeServerVersionPayload decode(RegistryFriendlyByteBuf buf) {
        return new NeoForgeServerVersionPayload(buf.readUtf(ServerVersionNotice.MAX_VERSION_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
