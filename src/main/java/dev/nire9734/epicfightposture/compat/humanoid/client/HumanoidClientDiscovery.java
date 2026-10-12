package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.HumanoidCompatDiscoveryStore;
import dev.nire9734.epicfightposture.compat.humanoid.HumanoidEnvironmentFingerprint;
import dev.nire9734.epicfightposture.network.BridgeNetwork;
import dev.nire9734.epicfightposture.network.HumanoidDiscoveryUploadPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
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
        Set<ResourceLocation> humanoid = new HashSet<>();
        Set<EntityType<?>> vanillaHumanoidTypes = new HashSet<>();

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
            if (livingRenderer.getModel() instanceof HumanoidModel<?>) {
                humanoid.add(id);
                vanillaHumanoidTypes.add(entityType);
            }
        }

        ScanSnapshot extra;
        try {
            extra = additionalScanner.scan(renderers);
        } catch (Throwable ignored) {
            extra = ScanSnapshot.empty();
        }

        inspected.addAll(extra.inspected());
        humanoid.addAll(extra.humanoid());

        Set<EntityType<?>> externalHumanoidTypes = new HashSet<>(extra.externalHumanoidTypes());
        return new ScanSnapshot(
                Set.copyOf(inspected),
                Set.copyOf(humanoid),
                Set.copyOf(vanillaHumanoidTypes),
                Set.copyOf(externalHumanoidTypes)
        );
    }

    public static void handleServerRequest(UUID nonce) {
        ScanSnapshot scan = scanCurrent();
        HumanoidCompatDiscoveryStore.save(scan.humanoid());
        BridgeNetwork.sendToServer(new HumanoidDiscoveryUploadPacket(
                nonce,
                HumanoidEnvironmentFingerprint.current(),
                scan.inspected(),
                scan.humanoid()
        ));
    }

    public record ScanSnapshot(
            Set<ResourceLocation> inspected,
            Set<ResourceLocation> humanoid,
            Set<EntityType<?>> vanillaHumanoidTypes,
            Set<EntityType<?>> externalHumanoidTypes) {
        public static ScanSnapshot empty() {
            return new ScanSnapshot(Set.of(), Set.of(), Set.of(), Set.of());
        }
    }

    private HumanoidClientDiscovery() {
    }
}
