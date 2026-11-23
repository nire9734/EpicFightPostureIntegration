package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.client.ServerVersionNotice;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The server's mod version, sent once at login so a client can flag a version mismatch. */
public final class ServerVersionPacket {
    private final String version;

    public ServerVersionPacket(String version) {
        this.version = version == null ? "" : version;
    }

    public static ServerVersionPacket current() {
        return new ServerVersionPacket(localModVersion());
    }

    /** This installation's mod version, as declared in mods.toml. */
    public static String localModVersion() {
        return ModList.get().getModContainerById(EpicFightPostureIntegration.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    public static void encode(ServerVersionPacket packet, FriendlyByteBuf buf) {
        String version = packet.version.length() > ServerVersionNotice.MAX_VERSION_LENGTH
                ? packet.version.substring(0, ServerVersionNotice.MAX_VERSION_LENGTH)
                : packet.version;
        buf.writeUtf(version, ServerVersionNotice.MAX_VERSION_LENGTH);
    }

    public static ServerVersionPacket decode(FriendlyByteBuf buf) {
        return new ServerVersionPacket(buf.readUtf(ServerVersionNotice.MAX_VERSION_LENGTH));
    }

    /** Registered with consumerMainThread; ServerVersionNotice is plain Java. */
    public static void handle(ServerVersionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerVersionNotice.receive(packet.version);
        contextSupplier.get().setPacketHandled(true);
    }
}
