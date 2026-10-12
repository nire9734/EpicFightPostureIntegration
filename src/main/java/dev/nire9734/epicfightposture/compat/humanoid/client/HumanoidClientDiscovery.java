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
    public static ScanSnapshot scanCurrent() {
        return scan(Minecraft.getInstance().getEntityRenderDispatcher().renderers);
    }

    public static ScanSnapshot scan(Map<EntityType<?>, EntityRenderer<?>> renderers) {
        Set<ResourceLocation> inspected = new HashSet<>();
        Set<ResourceLocation> humanoid = new HashSet<>();
        Set<EntityType<?>> humanoidTypes = new HashSet<>();

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
                humanoidTypes.add(entityType);
            }
        }

        return new ScanSnapshot(Set.copyOf(inspected), Set.copyOf(humanoid), Set.copyOf(humanoidTypes));
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
            Set<EntityType<?>> humanoidTypes) {
    }

    private HumanoidClientDiscovery() {
    }
}
