package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.PostureServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Server-authoritative EFPI policy synchronized to a client after login. */
public final class ServerSettingsSyncPacket {
    private static final int MAX_BLACKLIST_IDS = 8192;

    private final PostureServerConfig.Snapshot snapshot;

    public ServerSettingsSyncPacket(PostureServerConfig.Snapshot snapshot) {
        this.snapshot = snapshot == null ? PostureServerConfig.localSnapshot() : snapshot;
    }

    public static ServerSettingsSyncPacket current() {
        return new ServerSettingsSyncPacket(PostureServerConfig.localSnapshot());
    }

    public static void encode(ServerSettingsSyncPacket packet, FriendlyByteBuf buf) {
        PostureServerConfig.Snapshot snapshot = packet.snapshot;
        buf.writeDouble(snapshot.globalMultiplier());
        buf.writeDouble(snapshot.minimumPosture());
        buf.writeBoolean(snapshot.useArmorScaling());
        buf.writeBoolean(snapshot.enableVanillaHumanoidCompatibility());
        buf.writeBoolean(snapshot.enableExtendedHumanoidCompatibility());
        buf.writeBoolean(snapshot.enableVanillaSpiderCompatibility());

        Set<ResourceLocation> blacklist = snapshot.entityBlacklist();
        if (blacklist.size() > MAX_BLACKLIST_IDS) {
            throw new IllegalArgumentException("EFPI entity blacklist exceeds network limit");
        }
        buf.writeVarInt(blacklist.size());
        for (ResourceLocation id : blacklist) {
            buf.writeResourceLocation(id);
        }
    }

    public static ServerSettingsSyncPacket decode(FriendlyByteBuf buf) {
        double globalMultiplier = buf.readDouble();
        double minimumPosture = buf.readDouble();
        boolean useArmorScaling = buf.readBoolean();
        boolean vanillaHumanoid = buf.readBoolean();
        boolean extendedHumanoid = buf.readBoolean();
        boolean vanillaSpider = buf.readBoolean();

        int blacklistCount = buf.readVarInt();
        if (blacklistCount < 0 || blacklistCount > MAX_BLACKLIST_IDS) {
            throw new IllegalArgumentException("Invalid EFPI entity blacklist size: " + blacklistCount);
        }
        Set<ResourceLocation> blacklist = new HashSet<>();
        for (int i = 0; i < blacklistCount; i++) {
            blacklist.add(buf.readResourceLocation());
        }

        return new ServerSettingsSyncPacket(new PostureServerConfig.Snapshot(
                globalMultiplier,
                minimumPosture,
                useArmorScaling,
                Set.copyOf(blacklist),
                vanillaHumanoid,
                extendedHumanoid,
                vanillaSpider
        ));
    }

    public static void handle(ServerSettingsSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> PostureServerConfig.applyRemoteSnapshot(packet.snapshot));
        context.setPacketHandled(true);
    }
}
