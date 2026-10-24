package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.HumanoidEnvironmentFingerprint;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import dev.nire9734.epicfightposture.network.BridgeNetwork;
import dev.nire9734.epicfightposture.network.HumanoidDiscoveryUploadPacket;
import dev.nire9734.epicfightposture.network.HumanoidDiscoveryRefreshPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client-only renderer inspection used by local auto-compat and dedicated-server reports. */
public final class HumanoidClientDiscovery {
    private static volatile ScanSnapshot serverReportSnapshot = ScanSnapshot.empty();

    public static ScanSnapshot scanCurrent() {
        return scan(Minecraft.getInstance().getEntityRenderDispatcher().renderers);
    }

    public static ScanSnapshot scan(Map<EntityType<?>, EntityRenderer<?>> renderers) {
        Set<ResourceLocation> inspected = new HashSet<>();
        Set<ResourceLocation> vanillaHumanoid = new HashSet<>();
        Set<ResourceLocation> vanillaSpider = new HashSet<>();
        Set<EntityType<?>> vanillaHumanoidTypes = new HashSet<>();
        Set<EntityType<?>> vanillaSpiderTypes = new HashSet<>();

        for (Map.Entry<EntityType<?>, EntityRenderer<?>> entry : renderers.entrySet()) {
            EntityType<?> entityType = entry.getKey();
            EntityRenderer<?> renderer = entry.getValue();
            if (entityType == null || entityType == EntityType.PLAYER
                    || !(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)) {
                continue;
            }

            ResourceLocation id = EntityType.getKey(entityType);
            if (id == null) {
                continue;
            }

            inspected.add(id);
            Object model = livingRenderer.getModel();
            if (model instanceof HumanoidModel<?>) {
                vanillaHumanoid.add(id);
                vanillaHumanoidTypes.add(entityType);
            } else if (model instanceof SpiderModel<?>) {
                vanillaSpider.add(id);
                vanillaSpiderTypes.add(entityType);
            }
        }

        HumanoidBackendRegistry.ScanResult extendedScan = HumanoidBackendRegistry.scan(renderers);
        inspected.addAll(extendedScan.inspected());
        Set<ResourceLocation> extendedHumanoid = new HashSet<>(extendedScan.compatible());
        Set<EntityType<?>> extendedHumanoidTypes = new HashSet<>(extendedScan.compatibleTypes());

        // Native Epic Fight patches and patches supplied by other mods already own
        // their armature/renderer contract. They are not automatic-compat targets
        // and must not be reported as unsupported either, otherwise mixed reports
        // can create false dedicated-server conflicts.
        Set<ResourceLocation> externallyPatched = VanillaHumanoidCompat.externallyPatchedIds();
        if (!externallyPatched.isEmpty()) {
            inspected.removeAll(externallyPatched);
            vanillaHumanoid.removeAll(externallyPatched);
            extendedHumanoid.removeAll(externallyPatched);
            vanillaSpider.removeAll(externallyPatched);
            vanillaHumanoidTypes.removeIf(type -> externallyPatched.contains(EntityType.getKey(type)));
            extendedHumanoidTypes.removeIf(type -> externallyPatched.contains(EntityType.getKey(type)));
            vanillaSpiderTypes.removeIf(type -> externallyPatched.contains(EntityType.getKey(type)));
        }

        Set<ResourceLocation> ambiguous = new HashSet<>();
        collectOverlap(ambiguous, vanillaHumanoid, extendedHumanoid);
        collectOverlap(ambiguous, vanillaHumanoid, vanillaSpider);
        collectOverlap(ambiguous, extendedHumanoid, vanillaSpider);
        if (!ambiguous.isEmpty()) {
            vanillaHumanoid.removeAll(ambiguous);
            extendedHumanoid.removeAll(ambiguous);
            vanillaSpider.removeAll(ambiguous);
            vanillaHumanoidTypes.removeIf(type -> ambiguous.contains(EntityType.getKey(type)));
            extendedHumanoidTypes.removeIf(type -> ambiguous.contains(EntityType.getKey(type)));
            vanillaSpiderTypes.removeIf(type -> ambiguous.contains(EntityType.getKey(type)));
        }

        return new ScanSnapshot(
                Set.copyOf(inspected),
                Set.copyOf(vanillaHumanoid),
                Set.copyOf(extendedHumanoid),
                Set.copyOf(vanillaSpider),
                Set.copyOf(vanillaHumanoidTypes),
                Set.copyOf(extendedHumanoidTypes),
                Set.copyOf(vanillaSpiderTypes)
        );
    }

    /**
     * Stores the renderer targets that actually passed client-side preparation.
     * Dedicated-server login reports reuse this immutable snapshot rather than
     * rerunning model conversion and mutating live renderer/model state.
     */
    public static void updateServerReportSnapshot(ScanSnapshot snapshot) {
        serverReportSnapshot = snapshot == null ? ScanSnapshot.empty() : snapshot;
    }

    /** Called only after the renderer reload has prepared its new validated snapshot. */
    public static void requestServerRescanAfterReload() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null && !minecraft.hasSingleplayerServer()) {
            BridgeNetwork.sendToServer(new HumanoidDiscoveryRefreshPacket());
        }
    }

    public static void handleServerRequest(UUID nonce) {
        ScanSnapshot scan = serverReportSnapshot;
        String fingerprint = HumanoidEnvironmentFingerprint.current();
        BridgeNetwork.sendToServer(new HumanoidDiscoveryUploadPacket(
                nonce,
                fingerprint,
                scan.inspected(),
                scan.vanillaHumanoid(),
                scan.extendedHumanoid(),
                scan.vanillaSpider()
        ));
    }

    private static void collectOverlap(
            Set<ResourceLocation> target,
            Set<ResourceLocation> first,
            Set<ResourceLocation> second) {
        Set<ResourceLocation> overlap = new HashSet<>(first);
        overlap.retainAll(second);
        target.addAll(overlap);
    }

    public record ScanSnapshot(
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> vanillaHumanoid,
            Set<ResourceLocation> extendedHumanoid,
            Set<ResourceLocation> vanillaSpider,
            Set<EntityType<?>> vanillaHumanoidTypes,
            Set<EntityType<?>> extendedHumanoidTypes,
            Set<EntityType<?>> vanillaSpiderTypes) {
        public ScanSnapshot {
            inspected = inspected == null ? Set.of() : Set.copyOf(inspected);
            vanillaHumanoid = vanillaHumanoid == null ? Set.of() : Set.copyOf(vanillaHumanoid);
            extendedHumanoid = extendedHumanoid == null ? Set.of() : Set.copyOf(extendedHumanoid);
            vanillaSpider = vanillaSpider == null ? Set.of() : Set.copyOf(vanillaSpider);
            vanillaHumanoidTypes = vanillaHumanoidTypes == null ? Set.of() : Set.copyOf(vanillaHumanoidTypes);
            extendedHumanoidTypes = extendedHumanoidTypes == null ? Set.of() : Set.copyOf(extendedHumanoidTypes);
            vanillaSpiderTypes = vanillaSpiderTypes == null ? Set.of() : Set.copyOf(vanillaSpiderTypes);
        }

        public Set<ResourceLocation> humanoid() {
            Set<ResourceLocation> combined = new HashSet<>(vanillaHumanoid);
            combined.addAll(extendedHumanoid);
            return Set.copyOf(combined);
        }

        public Set<ResourceLocation> automaticTargets() {
            Set<ResourceLocation> combined = new HashSet<>(humanoid());
            combined.addAll(vanillaSpider);
            return Set.copyOf(combined);
        }

        public static ScanSnapshot empty() {
            return new ScanSnapshot(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        }
    }

    private HumanoidClientDiscovery() {
    }
}
