package dev.nire9734.epicfightposture.neoforge.discovery;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded, disjoint source-renderer classifications (protocol 2). */
public record NeoForgeRendererScanReport(UUID nonce, String fingerprint,
        Set<ResourceLocation> inspected, Set<ResourceLocation> vanillaHumanoids,
        Set<ResourceLocation> extendedHumanoids, Set<ResourceLocation> spiders)
        implements CustomPacketPayload {
    public static final int MAX_IDS = 8192;
    public static final Type<NeoForgeRendererScanReport> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("epicfight_posture_integration", "renderer_scan_report"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NeoForgeRendererScanReport> STREAM_CODEC =
            StreamCodec.of(NeoForgeRendererScanReport::encode, NeoForgeRendererScanReport::decode);

    public NeoForgeRendererScanReport {
        inspected = Set.copyOf(inspected);
        vanillaHumanoids = Set.copyOf(vanillaHumanoids);
        extendedHumanoids = Set.copyOf(extendedHumanoids);
        spiders = Set.copyOf(spiders);
    }
    private static void encode(RegistryFriendlyByteBuf buffer, NeoForgeRendererScanReport packet) {
        buffer.writeUUID(packet.nonce());
        buffer.writeUtf(packet.fingerprint(), 64);
        writeIds(buffer, packet.inspected());
        writeIds(buffer, packet.vanillaHumanoids());
        writeIds(buffer, packet.extendedHumanoids());
        writeIds(buffer, packet.spiders());
    }
    private static NeoForgeRendererScanReport decode(RegistryFriendlyByteBuf buffer) {
        return new NeoForgeRendererScanReport(buffer.readUUID(), buffer.readUtf(64),
                readIds(buffer), readIds(buffer), readIds(buffer), readIds(buffer));
    }
    private static void writeIds(RegistryFriendlyByteBuf buffer, Set<ResourceLocation> ids) {
        if (ids.size() > MAX_IDS) throw new IllegalArgumentException("Excessive renderer report size");
        buffer.writeVarInt(ids.size());
        ids.stream().sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(buffer::writeResourceLocation);
    }
    private static Set<ResourceLocation> readIds(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_IDS) throw new IllegalArgumentException("Invalid renderer report count");
        Set<ResourceLocation> ids = new HashSet<>();
        for (int i = 0; i < count; i++) ids.add(buffer.readResourceLocation());
        return Set.copyOf(ids);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
