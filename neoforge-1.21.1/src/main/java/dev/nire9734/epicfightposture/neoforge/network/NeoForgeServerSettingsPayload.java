package dev.nire9734.epicfightposture.neoforge.network;

import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** NeoForge counterpart of Forge's authoritative settings packet (same fields since 3.3.0). */
public record NeoForgeServerSettingsPayload(NeoForgeServerSettings.Snapshot snapshot)
        implements CustomPacketPayload {
    private static final int MAX_BLACKLIST_IDS = 8192;
    private static final int MAX_MOD_IDS = 2048;
    private static final int MAX_MOD_ID_LENGTH = 128;

    public static final Type<NeoForgeServerSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("epicfight_posture_integration", "server_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NeoForgeServerSettingsPayload> STREAM_CODEC =
            StreamCodec.of(NeoForgeServerSettingsPayload::encode, NeoForgeServerSettingsPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, NeoForgeServerSettingsPayload payload) {
        NeoForgeServerSettings.Snapshot settings = payload.snapshot();
        buf.writeBoolean(settings.vanillaHumanoid());
        buf.writeBoolean(settings.extendedHumanoid());
        buf.writeBoolean(settings.vanillaSpider());
        if (settings.entityBlacklist().size() > MAX_BLACKLIST_IDS) {
            throw new IllegalArgumentException("EFPI entity blacklist exceeds network limit");
        }
        buf.writeVarInt(settings.entityBlacklist().size());
        for (ResourceLocation id : settings.entityBlacklist()) buf.writeResourceLocation(id);
        writeNamespaces(buf, settings.modAllowlist());
        writeNamespaces(buf, settings.modDenylist());
    }

    private static NeoForgeServerSettingsPayload decode(RegistryFriendlyByteBuf buf) {
        boolean vanilla = buf.readBoolean();
        boolean extended = buf.readBoolean();
        boolean spider = buf.readBoolean();

        int count = buf.readVarInt();
        if (count < 0 || count > MAX_BLACKLIST_IDS) {
            throw new IllegalArgumentException("Invalid EFPI entity blacklist size: " + count);
        }
        Set<ResourceLocation> blacklist = new HashSet<>();
        for (int i = 0; i < count; i++) blacklist.add(buf.readResourceLocation());

        Set<String> allow = readNamespaces(buf);
        Set<String> deny = readNamespaces(buf);
        return new NeoForgeServerSettingsPayload(new NeoForgeServerSettings.Snapshot(
                vanilla, extended, spider, blacklist, allow, deny));
    }

    private static void writeNamespaces(RegistryFriendlyByteBuf buf, Set<String> ids) {
        if (ids.size() > MAX_MOD_IDS) {
            throw new IllegalArgumentException("EFPI mod namespace list exceeds network limit");
        }
        buf.writeVarInt(ids.size());
        for (String id : ids) buf.writeUtf(id, MAX_MOD_ID_LENGTH);
    }

    private static Set<String> readNamespaces(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_MOD_IDS) {
            throw new IllegalArgumentException("Invalid EFPI mod namespace list size: " + count);
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(MAX_MOD_ID_LENGTH);
            if (id.isBlank()) throw new IllegalArgumentException("Blank EFPI mod namespace");
            ids.add(id);
        }
        return Set.copyOf(ids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
