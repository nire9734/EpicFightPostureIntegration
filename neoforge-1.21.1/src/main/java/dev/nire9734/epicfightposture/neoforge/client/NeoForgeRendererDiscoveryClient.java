package dev.nire9734.epicfightposture.neoforge.client;
import java.util.Set;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeEnvironmentFingerprint;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeRendererScanReport;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeRendererRescanRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Last verified scan shared only with an authorized server request. */
public final class NeoForgeRendererDiscoveryClient {
    private record Snapshot(Set<ResourceLocation> inspected, Set<ResourceLocation> vanilla,
                            Set<ResourceLocation> extended, Set<ResourceLocation> spiders) {
        private Snapshot {
            inspected = Set.copyOf(inspected);
            vanilla = Set.copyOf(vanilla);
            extended = Set.copyOf(extended);
            spiders = Set.copyOf(spiders);
        }
    }
    private static volatile Snapshot lastScan = new Snapshot(Set.of(), Set.of(), Set.of(), Set.of());

    public static void record(Set<ResourceLocation> inspected, Set<ResourceLocation> vanilla,
                              Set<ResourceLocation> extended, Set<ResourceLocation> spiders) {
        lastScan = new Snapshot(inspected, vanilla, extended, spiders);
    }
    public static void requestServerRescanAfterReload() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null && !minecraft.hasSingleplayerServer()) {
            PacketDistributor.sendToServer(new NeoForgeRendererRescanRequest());
        }
    }
    public static void replyToServer(java.util.UUID nonce) {
        Snapshot s = lastScan;
        PacketDistributor.sendToServer(new NeoForgeRendererScanReport(
                nonce, NeoForgeEnvironmentFingerprint.current(), s.inspected(),
                s.vanilla(), s.extended(), s.spiders()));
    }
    private NeoForgeRendererDiscoveryClient() {}
}
