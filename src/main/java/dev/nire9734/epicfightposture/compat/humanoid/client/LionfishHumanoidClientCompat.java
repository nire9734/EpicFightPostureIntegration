package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import yesman.epicfight.api.client.model.SkinnedMesh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Experimental Lionfish/Citadel-style humanoid discovery and mesh preparation.
 *
 * <p>This remains shadow-only: successful descriptors and meshes are cached for
 * validation, but no entity patch or renderer is registered yet.</p>
 */
public final class LionfishHumanoidClientCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<EntityType<?>, HumanoidSkeletonDescriptor> RUNTIME_SKELETONS = new HashMap<>();
    private static final Map<EntityType<?>, SkinnedMesh> RUNTIME_MESHES = new HashMap<>();

    public static void shadowScan(Map<EntityType<?>, EntityRenderer<?>> renderers) {
        RUNTIME_SKELETONS.clear();
        RUNTIME_MESHES.clear();
        int inspected = 0;
        int resolved = 0;
        int meshed = 0;
        List<ResourceLocation> ready = new ArrayList<>();

        for (Map.Entry<EntityType<?>, EntityRenderer<?>> entry : renderers.entrySet()) {
            EntityType<?> entityType = entry.getKey();
            EntityRenderer<?> renderer = entry.getValue();
            if (entityType == null || entityType == EntityType.PLAYER
                    || !(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)
                    || !(livingRenderer.getModel() instanceof AdvancedEntityModel<?> advancedModel)) {
                continue;
            }

            ResourceLocation entityId = EntityType.getKey(entityType);
            if (entityId == null || VanillaHumanoidCompat.isExternallyPatched(entityId)) {
                continue;
            }
            inspected++;

            SkeletonKey key = new SkeletonKey(entityId, advancedModel.getClass().getName());
            LionfishHumanoidMeshAdapter.Result result = LionfishHumanoidMeshAdapter.analyseAndBake(advancedModel, key);
            if (!result.supported() || result.descriptor() == null) {
                LOGGER.debug("EFPI Lionfish shadow resolver rejected {}: {}", entityId, result.reason());
                continue;
            }

            HumanoidSkeletonDescriptor descriptor = result.descriptor();
            RUNTIME_SKELETONS.put(entityType, descriptor);
            resolved++;

            if (result.mesh() != null) {
                RUNTIME_MESHES.put(entityType, result.mesh());
                meshed++;
                ready.add(entityId);
            }

            LOGGER.debug(
                    "EFPI Lionfish shadow accepted {}: height={}, shoulderWidth={}, armL={}/{}, armR={}/{}, virtualJoints={}, confidence={}, mesh={}",
                    entityId,
                    descriptor.metrics().totalHeight(),
                    descriptor.metrics().shoulderWidth(),
                    descriptor.metrics().upperArmLeft(),
                    descriptor.metrics().lowerArmLeft(),
                    descriptor.metrics().upperArmRight(),
                    descriptor.metrics().lowerArmRight(),
                    descriptor.quality().virtualJointCount(),
                    descriptor.quality().overallConfidence(),
                    result.mesh() != null
            );
        }

        LOGGER.info(
                "EFPI Lionfish humanoid shadow scan: {} inspected, {} resolved, {} mesh-ready; ready targets: {}",
                inspected,
                resolved,
                meshed,
                ready
        );
    }

    static HumanoidSkeletonDescriptor runtimeSkeleton(EntityType<?> entityType) {
        return RUNTIME_SKELETONS.get(entityType);
    }

    static SkinnedMesh runtimeMesh(EntityType<?> entityType) {
        return RUNTIME_MESHES.get(entityType);
    }

    private LionfishHumanoidClientCompat() {}
}
