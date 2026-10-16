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
    private static final int MAX_MOD_IDS = 2048;
    private static final int MAX_MOD_ID_LENGTH = 128;

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
        buf.writeEnum(snapshot.compatibilityFilterMode());

        Set<ResourceLocation> blacklist = snapshot.entityBlacklist();
        if (blacklist.size() > MAX_BLACKLIST_IDS) {
            throw new IllegalArgumentException("EFPI entity blacklist exceeds network limit");
        }
        buf.writeVarInt(blacklist.size());
        for (ResourceLocation id : blacklist) {
            buf.writeResourceLocation(id);
        }

        writeModIds(buf, snapshot.modAllowlist(), "allowlist");
        writeModIds(buf, snapshot.modDenylist(), "denylist");
    }

    public static ServerSettingsSyncPacket decode(FriendlyByteBuf buf) {
        double globalMultiplier = buf.readDouble();
        double minimumPosture = buf.readDouble();
        boolean useArmorScaling = buf.readBoolean();
        boolean vanillaHumanoid = buf.readBoolean();
        boolean extendedHumanoid = buf.readBoolean();
        boolean vanillaSpider = buf.readBoolean();
        PostureServerConfig.CompatibilityFilterMode filterMode =
                buf.readEnum(PostureServerConfig.CompatibilityFilterMode.class);

        int blacklistCount = buf.readVarInt();
        if (blacklistCount < 0 || blacklistCount > MAX_BLACKLIST_IDS) {
            throw new IllegalArgumentException("Invalid EFPI entity blacklist size: " + blacklistCount);
        }
        Set<ResourceLocation> blacklist = new HashSet<>();
        for (int i = 0; i < blacklistCount; i++) {
            blacklist.add(buf.readResourceLocation());
        }

        Set<String> allowlist = readModIds(buf, "allowlist");
        Set<String> denylist = readModIds(buf, "denylist");

        return new ServerSettingsSyncPacket(new PostureServerConfig.Snapshot(
                globalMultiplier,
                minimumPosture,
                useArmorScaling,
                Set.copyOf(blacklist),
                vanillaHumanoid,
                extendedHumanoid,
                vanillaSpider,
                filterMode,
                allowlist,
                denylist
        ));
    }

    private static void writeModIds(FriendlyByteBuf buf, Set<String> ids, String label) {
        if (ids.size() > MAX_MOD_IDS) {
            throw new IllegalArgumentException("EFPI mod " + label + " exceeds network limit");
        }
        buf.writeVarInt(ids.size());
        for (String id : ids) {
            buf.writeUtf(id, MAX_MOD_ID_LENGTH);
        }
    }

    private static Set<String> readModIds(FriendlyByteBuf buf, String label) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_MOD_IDS) {
            throw new IllegalArgumentException("Invalid EFPI mod " + label + " size: " + count);
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(buf.readUtf(MAX_MOD_ID_LENGTH));
        }
        return Set.copyOf(ids);
    }

    public static void handle(ServerSettingsSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> PostureServerConfig.applyRemoteSnapshot(packet.snapshot));
        context.setPacketHandled(true);
    }
}
