package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.HumanoidCompatDiscoveryStore;
import dev.nire9734.epicfightposture.compat.humanoid.HumanoidEnvironmentFingerprint;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import dev.nire9734.epicfightposture.network.BridgeNetwork;
import dev.nire9734.epicfightposture.network.HumanoidDiscoveryUploadPacket;
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
    @FunctionalInterface
    public interface AdditionalScanner {
        ScanSnapshot scan(Map<EntityType<?>, EntityRenderer<?>> renderers);
    }

    private static volatile AdditionalScanner additionalScanner = renderers -> ScanSnapshot.empty();

    public static void installAdditionalScanner(AdditionalScanner scanner) {
        additionalScanner = scanner == null ? renderers -> ScanSnapshot.empty() : scanner;
    }

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

        ScanSnapshot extra;
        try {
            extra = additionalScanner.scan(renderers);
        } catch (Throwable ignored) {
            extra = ScanSnapshot.empty();
        }

        inspected.addAll(extra.inspected());
        vanillaHumanoid.addAll(extra.vanillaHumanoid());
        vanillaSpider.addAll(extra.vanillaSpider());
        Set<ResourceLocation> geckoHumanoid = new HashSet<>(extra.geckoLibHumanoid());
        Set<EntityType<?>> externalHumanoidTypes = new HashSet<>(extra.externalHumanoidTypes());

        // Native Epic Fight patches and patches supplied by other mods already own
        // their armature/renderer contract. They are not automatic-compat targets
        // and must not be reported as unsupported either, otherwise mixed reports
        // can create false dedicated-server conflicts.
        Set<ResourceLocation> externallyPatched = VanillaHumanoidCompat.externallyPatchedIds();
        if (!externallyPatched.isEmpty()) {
            inspected.removeAll(externallyPatched);
            vanillaHumanoid.removeAll(externallyPatched);
            geckoHumanoid.removeAll(externallyPatched);
            vanillaSpider.removeAll(externallyPatched);
            vanillaHumanoidTypes.removeIf(type -> externallyPatched.contains(EntityType.getKey(type)));
            externalHumanoidTypes.removeIf(type -> externallyPatched.contains(EntityType.getKey(type)));
            vanillaSpiderTypes.removeIf(type -> externallyPatched.contains(EntityType.getKey(type)));
        }

        Set<ResourceLocation> ambiguous = new HashSet<>();
        collectOverlap(ambiguous, vanillaHumanoid, geckoHumanoid);
        collectOverlap(ambiguous, vanillaHumanoid, vanillaSpider);
        collectOverlap(ambiguous, geckoHumanoid, vanillaSpider);
        if (!ambiguous.isEmpty()) {
            vanillaHumanoid.removeAll(ambiguous);
            geckoHumanoid.removeAll(ambiguous);
            vanillaSpider.removeAll(ambiguous);
            vanillaHumanoidTypes.removeIf(type -> ambiguous.contains(EntityType.getKey(type)));
            externalHumanoidTypes.removeIf(type -> ambiguous.contains(EntityType.getKey(type)));
            vanillaSpiderTypes.removeIf(type -> ambiguous.contains(EntityType.getKey(type)));
        }

        return new ScanSnapshot(
                Set.copyOf(inspected),
                Set.copyOf(vanillaHumanoid),
                Set.copyOf(geckoHumanoid),
                Set.copyOf(vanillaSpider),
                Set.copyOf(vanillaHumanoidTypes),
                Set.copyOf(externalHumanoidTypes),
                Set.copyOf(vanillaSpiderTypes)
        );
    }

    public static void handleServerRequest(UUID nonce) {
        ScanSnapshot scan = scanCurrent();
        String fingerprint = HumanoidEnvironmentFingerprint.current();
        HumanoidCompatDiscoveryStore.save(
                fingerprint,
                scan.vanillaHumanoid(),
                scan.geckoLibHumanoid(),
                scan.vanillaSpider()
        );
        BridgeNetwork.sendToServer(new HumanoidDiscoveryUploadPacket(
                nonce,
                fingerprint,
                scan.inspected(),
                scan.vanillaHumanoid(),
                scan.geckoLibHumanoid(),
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
            Set<ResourceLocation> geckoLibHumanoid,
            Set<ResourceLocation> vanillaSpider,
            Set<EntityType<?>> vanillaHumanoidTypes,
            Set<EntityType<?>> externalHumanoidTypes,
            Set<EntityType<?>> vanillaSpiderTypes) {
        public ScanSnapshot {
            inspected = inspected == null ? Set.of() : Set.copyOf(inspected);
            vanillaHumanoid = vanillaHumanoid == null ? Set.of() : Set.copyOf(vanillaHumanoid);
            geckoLibHumanoid = geckoLibHumanoid == null ? Set.of() : Set.copyOf(geckoLibHumanoid);
            vanillaSpider = vanillaSpider == null ? Set.of() : Set.copyOf(vanillaSpider);
            vanillaHumanoidTypes = vanillaHumanoidTypes == null ? Set.of() : Set.copyOf(vanillaHumanoidTypes);
            externalHumanoidTypes = externalHumanoidTypes == null ? Set.of() : Set.copyOf(externalHumanoidTypes);
            vanillaSpiderTypes = vanillaSpiderTypes == null ? Set.of() : Set.copyOf(vanillaSpiderTypes);
        }

        public Set<ResourceLocation> humanoid() {
            Set<ResourceLocation> combined = new HashSet<>(vanillaHumanoid);
            combined.addAll(geckoLibHumanoid);
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
