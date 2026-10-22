package dev.nire9734.epicfightposture.neoforge.network;

import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** NeoForge counterpart of Forge 3.0.2's authoritative settings packet. */
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
        buf.writeDouble(settings.globalMultiplier());
        buf.writeDouble(settings.minimumPosture());
        buf.writeBoolean(settings.useArmorScaling());
        buf.writeBoolean(settings.vanillaHumanoid());
        buf.writeBoolean(settings.extendedHumanoid());
        buf.writeBoolean(settings.vanillaSpider());
        // Matches the Forge packet's allowlist/denylist mode (derived from the allowlist).
        buf.writeBoolean(!settings.modAllowlist().isEmpty());
        if (settings.entityBlacklist().size() > MAX_BLACKLIST_IDS) {
            throw new IllegalArgumentException("EFPI entity blacklist exceeds network limit");
        }
        buf.writeVarInt(settings.entityBlacklist().size());
        for (ResourceLocation id : settings.entityBlacklist()) buf.writeResourceLocation(id);
        writeNamespaces(buf, settings.modAllowlist());
        writeNamespaces(buf, settings.modDenylist());
    }

    private static NeoForgeServerSettingsPayload decode(RegistryFriendlyByteBuf buf) {
        double multiplier = buf.readDouble();
        double minimum = buf.readDouble();
        boolean armor = buf.readBoolean();
        boolean vanilla = buf.readBoolean();
        boolean extended = buf.readBoolean();
        boolean spider = buf.readBoolean();
        boolean allowlistMode = buf.readBoolean();

        int count = buf.readVarInt();
        if (count < 0 || count > MAX_BLACKLIST_IDS) {
            throw new IllegalArgumentException("Invalid EFPI entity blacklist size: " + count);
        }
        Set<ResourceLocation> blacklist = new HashSet<>();
        for (int i = 0; i < count; i++) blacklist.add(buf.readResourceLocation());

        Set<String> allow = readNamespaces(buf);
        Set<String> deny = readNamespaces(buf);
        // Mode exists for Forge wire-format parity; the list itself remains authoritative.
        if (allowlistMode != !allow.isEmpty()) {
            throw new IllegalArgumentException("Inconsistent EFPI compatibility filter mode");
        }
        return new NeoForgeServerSettingsPayload(new NeoForgeServerSettings.Snapshot(
                multiplier, minimum, armor, vanilla, extended, spider,
                blacklist, allow, deny));
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
