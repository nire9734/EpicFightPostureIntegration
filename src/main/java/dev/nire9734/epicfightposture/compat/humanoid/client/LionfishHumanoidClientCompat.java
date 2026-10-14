package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import dev.nire9734.epicfightposture.compat.humanoid.rig.ConservativeHumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Experimental Lionfish/Citadel-style humanoid discovery.
 *
 * <p>This is intentionally shadow-only for now. It validates that the generic
 * source-rig and semantic resolver can classify real Lionfish models without
 * changing patch registration or rendering behavior.</p>
 */
public final class LionfishHumanoidClientCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<EntityType<?>, HumanoidSkeletonDescriptor> RUNTIME_SKELETONS = new HashMap<>();

    public static void shadowScan(Map<EntityType<?>, EntityRenderer<?>> renderers) {
        RUNTIME_SKELETONS.clear();
        int inspected = 0;
        int resolved = 0;

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

            try {
                SkeletonKey key = new SkeletonKey(
                        entityId,
                        advancedModel.getClass().getName()
                );
                HumanoidSkeletonDescriptor descriptor = LionfishSourceRigAdapter.resolve(
                        advancedModel,
                        key,
                        ConservativeHumanoidSemanticAnalyzer.INSTANCE
                ).descriptor();
                RUNTIME_SKELETONS.put(entityType, descriptor);
                resolved++;

                LOGGER.debug(
                        "EFPI Lionfish shadow resolver accepted {}: height={}, shoulderWidth={}, armL={}/{}, armR={}/{}, virtualJoints={}, confidence={}",
                        entityId,
                        descriptor.metrics().totalHeight(),
                        descriptor.metrics().shoulderWidth(),
                        descriptor.metrics().upperArmLeft(),
                        descriptor.metrics().lowerArmLeft(),
                        descriptor.metrics().upperArmRight(),
                        descriptor.metrics().lowerArmRight(),
                        descriptor.quality().virtualJointCount(),
                        descriptor.quality().overallConfidence()
                );
            } catch (Throwable throwable) {
                LOGGER.debug(
                        "EFPI Lionfish shadow resolver rejected {}: {}",
                        entityId,
                        throwable.getMessage()
                );
            }
        }

        LOGGER.info(
                "EFPI Lionfish humanoid shadow scan resolved {}/{} unpatched AdvancedEntityModel entity type(s)",
                resolved,
                inspected
        );
    }

    static HumanoidSkeletonDescriptor runtimeSkeleton(EntityType<?> entityType) {
        return RUNTIME_SKELETONS.get(entityType);
    }

    private LionfishHumanoidClientCompat() {
    }
}
