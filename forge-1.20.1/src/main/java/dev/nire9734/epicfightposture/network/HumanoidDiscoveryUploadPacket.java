package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.compat.humanoid.HumanoidDiscoveryServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** OP-client renderer classification report sent to a dedicated server. */
public final class HumanoidDiscoveryUploadPacket {
    public static final int MAX_ENTITY_IDS = 8192;
    private static final int FINGERPRINT_LENGTH = 64;

    private final UUID nonce;
    private final String environmentFingerprint;
    private final Set<ResourceLocation> inspected;
    private final Set<ResourceLocation> vanillaHumanoid;
    private final Set<ResourceLocation> extendedHumanoid;
    private final Set<ResourceLocation> vanillaSpider;

    public HumanoidDiscoveryUploadPacket(
            UUID nonce,
            String environmentFingerprint,
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> extendedHumanoid,
            Set<ResourceLocation> vanillaSpider) {
        this.nonce = nonce;
        this.environmentFingerprint = environmentFingerprint == null ? "" : environmentFingerprint;
        this.inspected = Set.copyOf(inspected);
        this.vanillaHumanoid = Set.copyOf(vanillaHumanoid);
        this.extendedHumanoid = Set.copyOf(extendedHumanoid);
        this.vanillaSpider = Set.copyOf(vanillaSpider);
    }

    public static void encode(HumanoidDiscoveryUploadPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.nonce);
        buf.writeUtf(packet.environmentFingerprint, FINGERPRINT_LENGTH);
        writeIds(buf, packet.inspected);
        writeIds(buf, packet.vanillaHumanoid);
        writeIds(buf, packet.extendedHumanoid);
        writeIds(buf, packet.vanillaSpider);
    }

    public static HumanoidDiscoveryUploadPacket decode(FriendlyByteBuf buf) {
        UUID nonce = buf.readUUID();
        String fingerprint = buf.readUtf(FINGERPRINT_LENGTH);
        Set<ResourceLocation> inspected = readIds(buf);
        Set<ResourceLocation> vanillaHumanoid = readIds(buf);
        Set<ResourceLocation> extendedHumanoid = readIds(buf);
        Set<ResourceLocation> vanillaSpider = readIds(buf);
        return new HumanoidDiscoveryUploadPacket(
                nonce,
                fingerprint,
                inspected,
                vanillaHumanoid,
                extendedHumanoid,
                vanillaSpider
        );
    }

    public static void handle(
            HumanoidDiscoveryUploadPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> HumanoidDiscoveryServer.handleUpload(
                sender,
                packet.nonce,
                packet.environmentFingerprint,
                packet.inspected,
                packet.vanillaHumanoid,
                packet.extendedHumanoid,
                packet.vanillaSpider
        ));
        context.setPacketHandled(true);
    }

    private static void writeIds(FriendlyByteBuf buf, Set<ResourceLocation> ids) {
        if (ids.size() > MAX_ENTITY_IDS) {
            throw new IllegalArgumentException("Too many entity IDs in renderer discovery packet");
        }
        buf.writeVarInt(ids.size());
        ids.stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(buf::writeResourceLocation);
    }

    private static Set<ResourceLocation> readIds(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTITY_IDS) {
            throw new IllegalArgumentException("Invalid renderer discovery entity count: " + count);
        }

        Set<ResourceLocation> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(buf.readResourceLocation());
        }
        return Set.copyOf(ids);
    }
}
