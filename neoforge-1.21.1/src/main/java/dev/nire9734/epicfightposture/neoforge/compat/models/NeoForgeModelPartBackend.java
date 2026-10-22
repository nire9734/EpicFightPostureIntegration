package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.neoforge.client.NeoForgePreparedRig;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import yesman.epicfight.api.asset.SelfAccessor;

/** Exact Forge 3.0.2 HierarchicalModel/ModelPart admission and mesh solver. */
public final class NeoForgeModelPartBackend {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static NeoForgePreparedRig inspect(EntityType<?> type, EntityRenderer<?> source) {
        if (!(source instanceof LivingEntityRenderer<?, ?> living)
                || !(living.getModel() instanceof HierarchicalModel<?> model)) return null;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return null;
        try {
            var result = RuntimeMeshOwnership.callOwned(() ->
                    ModelPartHumanoidMeshAdapter.analyseAndBake(
                            model, new SkeletonKey(id.toString(), model.getClass().getName())));
            if (!result.supported() || result.descriptor() == null || result.mesh() == null) {
                LOGGER.debug("EFPI NeoForge ModelPart rejected {}: {}", id, result.reason());
                return null;
            }
            var meshId = ResourceLocation.fromNamespaceAndPath(
                    "epicfight_posture_integration", "runtime_modelpart/" + id.getNamespace()
                    + "/" + id.getPath());
            LOGGER.debug("EFPI NeoForge ModelPart accepted {}: confidence={}, attachments={}",
                    id, result.descriptor().quality().overallConfidence(),
                    result.attachments().entries().size());
            return new NeoForgePreparedRig(SelfAccessor.create(meshId, result.mesh()),
                    result.descriptor(), "modelpart", result.attachments());
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.debug("EFPI NeoForge ModelPart model preparation failed for {}", id, exception);
            return null;
        }
    }

    private NeoForgeModelPartBackend() {}
}
