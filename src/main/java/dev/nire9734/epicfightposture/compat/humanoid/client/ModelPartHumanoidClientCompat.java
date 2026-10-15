package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.SelfAccessor;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Generic Minecraft HierarchicalModel/ModelPart humanoid backend. */
public final class ModelPartHumanoidClientCompat implements HumanoidRenderBackend {
    public static final ModelPartHumanoidClientCompat INSTANCE = new ModelPartHumanoidClientCompat();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<EntityType<?>, ModelPartAttachmentPlan> ATTACHMENTS = new HashMap<>();

    @Override
    public String id() {
        return "modelpart";
    }

    @Override
    public synchronized Inspection inspect(EntityType<?> entityType, EntityRenderer<?> renderer) {
        ATTACHMENTS.remove(entityType);

        if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)
                || !(livingRenderer.getModel() instanceof HierarchicalModel<?> hierarchicalModel)) {
            return Inspection.unmatched();
        }

        ResourceLocation entityId = EntityType.getKey(entityType);
        if (entityId == null) {
            return Inspection.rejected();
        }

        try {
            SkeletonKey key = new SkeletonKey(entityId.toString(), hierarchicalModel.getClass().getName());
            ModelPartHumanoidMeshAdapter.Result result =
                    ModelPartHumanoidMeshAdapter.analyseAndBake(hierarchicalModel, key);
            if (!result.supported() || result.descriptor() == null || result.mesh() == null) {
                LOGGER.debug("EFPI ModelPart humanoid rejected {}: {}", entityId, result.reason());
                return Inspection.rejected();
            }

            HumanoidSkeletonDescriptor descriptor = result.descriptor();
            ResourceLocation runtimeMeshId = new ResourceLocation(
                    EpicFightPostureIntegration.MODID,
                    "runtime_modelpart/" + entityId.getNamespace() + "/" + entityId.getPath()
            );
            AssetAccessor<SkinnedMesh> mesh = SelfAccessor.create(runtimeMeshId, result.mesh());
            ATTACHMENTS.put(entityType, result.attachments());

            LOGGER.debug(
                    "EFPI ModelPart humanoid accepted {}: height={}, shoulderWidth={}, armL={}/{}, armR={}/{}, virtualJoints={}, confidence={}, attachmentParts={}",
                    entityId,
                    descriptor.metrics().totalHeight(),
                    descriptor.metrics().shoulderWidth(),
                    descriptor.metrics().upperArmLeft(),
                    descriptor.metrics().lowerArmLeft(),
                    descriptor.metrics().upperArmRight(),
                    descriptor.metrics().lowerArmRight(),
                    descriptor.quality().virtualJointCount(),
                    descriptor.quality().overallConfidence(),
                    result.attachments().entries().size()
            );

            return Inspection.supported(new PreparedHumanoidModel(
                    id(),
                    entityType,
                    entityId,
                    mesh,
                    Optional.of(descriptor)
            ));
        } catch (Throwable throwable) {
            LOGGER.debug("EFPI ModelPart humanoid inspection failed for {}: {}", entityId, throwable.toString());
            return Inspection.rejected();
        }
    }

    @Override
    public synchronized void addPatchedRenderer(PatchedRenderersEvent.Add event, PreparedHumanoidModel prepared) {
        ModelPartAttachmentPlan attachments = ATTACHMENTS.get(prepared.entityType());
        HumanoidSkeletonDescriptor descriptor = prepared.skeleton().orElse(null);
        event.addPatchedEntityRenderer(
                prepared.entityType(),
                type -> new SpecialStateLionfishRenderer(
                        prepared.mesh(),
                        event.getContext(),
                        attachments,
                        descriptor)
        );
    }

    private ModelPartHumanoidClientCompat() {
    }
}
