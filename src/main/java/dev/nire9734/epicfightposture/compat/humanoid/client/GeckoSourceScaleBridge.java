package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recovers the model scale that a Gecko source renderer would normally apply in
 * preRender(). EFPI owns the special-state body pass, so Gecko's normal render
 * path is skipped and that scale would otherwise be lost.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class GeckoSourceScaleBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Class<?>> WARNED_RENDERERS = ConcurrentHashMap.newKeySet();
    private static final float EPSILON = 1.0E-5F;

    private GeckoSourceScaleBridge() {}

    static void apply(
            LivingEntity entity,
            EntityRenderer<LivingEntity> sourceRenderer,
            MultiBufferSource bufferSource,
            PoseStack targetPoseStack,
            int packedLight,
            int packedOverlay,
            float partialTicks) {
        if (!(sourceRenderer instanceof GeoEntityRenderer geoRenderer)
                || !(entity instanceof GeoAnimatable animatable)) {
            return;
        }

        try {
            GeoModel geoModel = geoRenderer.getGeoModel();
            ResourceLocation modelLocation = geoModel.getModelResource(animatable);
            BakedGeoModel bakedModel = geoModel.getBakedModel(modelLocation);
            if (bakedModel == null) {
                return;
            }

            ResourceLocation texture = geoRenderer.getTextureLocation(animatable);
            RenderType renderType = geoRenderer.getRenderType(animatable, texture, bufferSource, partialTicks);
            VertexConsumer baseBuffer = renderType == null ? null : bufferSource.getBuffer(renderType);

            // Probe preRender on an identity stack and copy only its scale into
            // Epic Fight's stack. This preserves source-defined sizing without
            // duplicating Gecko rotations/translations that can conflict with EF.
            PoseStack probe = new PoseStack();
            geoRenderer.preRender(
                    probe,
                    animatable,
                    bakedModel,
                    bufferSource,
                    baseBuffer,
                    false,
                    partialTicks,
                    packedLight,
                    packedOverlay,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );

            Vector3f scale = probe.last().pose().getScale(new Vector3f());
            if (!isUsableScale(scale)) {
                return;
            }

            if (Math.abs(scale.x - 1.0F) > EPSILON
                    || Math.abs(scale.y - 1.0F) > EPSILON
                    || Math.abs(scale.z - 1.0F) > EPSILON) {
                targetPoseStack.scale(scale.x, scale.y, scale.z);
            }
        } catch (Throwable throwable) {
            if (WARNED_RENDERERS.add(sourceRenderer.getClass())) {
                LOGGER.warn(
                        "EFPI could not recover Gecko source scale for {} ({})",
                        sourceRenderer.getClass().getName(),
                        entity.getType(),
                        throwable
                );
            }
        }
    }

    private static boolean isUsableScale(Vector3f scale) {
        return Float.isFinite(scale.x)
                && Float.isFinite(scale.y)
                && Float.isFinite(scale.z)
                && Math.abs(scale.x) > EPSILON
                && Math.abs(scale.y) > EPSILON
                && Math.abs(scale.z) > EPSILON;
    }
}
