package dev.nire9734.epicfightposture.neoforge.client;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Set;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryUpload;
import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeEnvironmentFingerprint;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeRendererScanReport;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeRendererRescanRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/** Last verified scan shared only with an authorized server request. */
public final class NeoForgeRendererDiscoveryClient {
    private static final Logger LOGGER = LogUtils.getLogger();
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
        if (!NeoForgeServerSettings.isLocalAutomaticCompatibilityEnabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null && !minecraft.hasSingleplayerServer()) {
            PacketDistributor.sendToServer(new NeoForgeRendererRescanRequest());
        }
    }
    public static void replyToServer(java.util.UUID nonce) {
        if (!NeoForgeServerSettings.isLocalAutomaticCompatibilityEnabled()) return;
        Snapshot s = lastScan;
        String fingerprint = NeoForgeEnvironmentFingerprint.current();
        List<RendererDiscoveryUpload.Chunk<ResourceLocation>> chunks;
        try {
            chunks = RendererDiscoveryUpload.split(s.inspected(), s.vanilla(), s.extended(), s.spiders(),
                    ResourceLocation::toString, RendererDiscoveryUpload.CHUNK_ID_BYTE_BUDGET);
        } catch (IllegalArgumentException tooLarge) {
            LOGGER.warn("EFPI could not send its renderer discovery report: {}", tooLarge.getMessage());
            return;
        }
        for (RendererDiscoveryUpload.Chunk<ResourceLocation> chunk : chunks) {
            PacketDistributor.sendToServer(new NeoForgeRendererScanReport(nonce, fingerprint, chunk));
        }
    }
    private NeoForgeRendererDiscoveryClient() {}
}
