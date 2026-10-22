package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.neoforge.client.NeoForgePreparedRig;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import yesman.epicfight.api.asset.SelfAccessor;

/** Lionfish API 3.1 advanced-entity bind mesh, without guessing bone names. */
public final class NeoForgeLionfishBackend {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("unchecked")
    public static void renderLayers(Mob mob, EntityRenderer<Mob> source,
                                    MultiBufferSource buffer, PoseStack pose, int light,
                                    float partialTicks, Armature armature, SkinnedMesh mesh,
                                    HumanoidSkeletonDescriptor skeleton) {
        if (!(source instanceof LivingEntityRenderer<?, ?> living)
                || !(living.getModel() instanceof AdvancedEntityModel<?>)) return;
        NeoForgeLivingRenderLayerBridge.replay(mob,
                (EntityRenderer<LivingEntity>) (Object) source, buffer, pose, light,
                partialTicks, armature, mesh, skeleton);
    }

    public static void clearLayerCaches() {
        LionfishSecondaryModelLayerBridge.clearCache();
        LayerPoseOverrideContext.clearCache();
    }

    public static NeoForgePreparedRig inspect(EntityType<?> type, EntityRenderer<?> source) {
        if (!(source instanceof LivingEntityRenderer<?, ?> living)
                || !(living.getModel() instanceof AdvancedEntityModel<?> model)) return null;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return null;
        try {
            LionfishHumanoidMeshAdapter.Result result = LionfishHumanoidMeshAdapter.analyseAndBake(
                    model, new SkeletonKey(id.toString(), model.getClass().getName()));
            if (!result.supported() || result.descriptor() == null || result.mesh() == null) {
                LOGGER.debug("EFPI NeoForge Lionfish rejected {} by shared model analysis: {}",
                        id, result.reason());
                return null;
            }
            float confidence = result.descriptor().quality().overallConfidence();
            if (!NeoForgeLionfishAdmission.accepts(true, true, true, confidence)) {
                LOGGER.debug("EFPI NeoForge Lionfish rejected {}: invalid rig confidence {}", id, confidence);
                return null;
            }
            // Forge 3.0.2 deliberately accepts successfully baked models even
            // below 0.75 confidence. Trust the shared semantic resolver.
            LOGGER.debug("EFPI NeoForge Lionfish accepted {}: confidence={}, virtualJoints={}",
                    id, confidence, result.descriptor().quality().virtualJointCount());
            ResourceLocation meshId = ResourceLocation.fromNamespaceAndPath(
                    "epicfight_posture_integration", "runtime_lionfish/" + id.getNamespace() + "/" + id.getPath());
            return new NeoForgePreparedRig(SelfAccessor.create(meshId, result.mesh()),
                    result.descriptor(), "lionfish");
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.debug("EFPI NeoForge Lionfish failed to inspect {}: {}",
                    id, exception.toString(), exception);
            return null;
        }
    }
    private NeoForgeLionfishBackend() {}
}
