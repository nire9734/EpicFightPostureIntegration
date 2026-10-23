package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges GeckoLib attachment rendering onto Epic Fight's final BIPED pose.
 *
 * <p>Normal Gecko render layers are replayed with the exact matrix composition
 * used by {@code SkinnedMesh.drawPosed}:</p>
 *
 * <pre>
 * attachment = (poseMatrix * joint.toOrigin) * geoBoneBind
 * </pre>
 *
 * <p>Attachment ownership is deliberately not classified here by a second set of
 * Gecko bone-name heuristics. Body baking, layer replay and renderer-specific
 * post-render attachment handling share {@link GeckoResolvedHumanoidRig}; direct
 * semantic source anchors use their canonical deformation joint while decoration
 * descendants select the dominant joint from the shared {@link HumanoidSkinWeightResolver}.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class GeckoAttachmentBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Class<?>> WARNED_RENDERERS = ConcurrentHashMap.newKeySet();
    private static final GeckoAttachmentCore.BoneAdapter ADAPTER = new GeckoAttachmentCore.BoneAdapter() {
        @Override public void resetBone(GeoBone bone) { resetBoneToInitial(bone); }
        @Override public void prepareMatrix(PoseStack bindStack, GeoBone bone) {
            RenderUtils.prepMatrixForBone(bindStack, bone);
        }
        @Override public void multiplyPose(PoseStack poseStack, Matrix4f transform) {
            poseStack.mulPoseMatrix(transform);
        }
        @Override public void applyJointAnimation(GeoBone bone, Armature armature, Pose pose, int jointId) {
            applyJointAnimationToSourceBone(bone, armature, pose, jointId);
        }
    };

    static void syncAndRenderLayers(
            LivingEntity entity,
            LivingEntityPatch<LivingEntity> entityPatch,
            EntityRenderer<LivingEntity> sourceRenderer,
            MultiBufferSource bufferSource,
            PoseStack poseStack,
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
            if (bakedModel == null || bakedModel.topLevelBones() == null) {
                return;
            }

            GeckoResolvedHumanoidRig semanticRig = GeckoResolvedHumanoidRig.resolve(
                    bakedModel,
                    GeckoResolvedHumanoidRig.keyFor(entity, geoModel, modelLocation),
                    GeckoBindPoseTransforms.OPERATIONS
            );

            for (GeoBone top : bakedModel.topLevelBones()) {
                GeckoAttachmentCore.resetTree(top, ADAPTER);
            }

            Armature armature = entityPatch.getArmature();
            OpenMatrix4f[] poseMatrices = armature.getPoseMatrices();
            List<GeoRenderLayer> layers = new ArrayList<>(geoRenderer.getRenderLayers());

            if (!layers.isEmpty()) {
                ResourceLocation texture = geoRenderer.getTextureLocation(animatable);
                RenderType renderType = geoRenderer.getRenderType(animatable, texture, bufferSource, partialTicks);
                VertexConsumer baseBuffer = renderType == null ? null : bufferSource.getBuffer(renderType);

                for (GeoRenderLayer layer : layers) {
                    layer.preRender(
                            poseStack,
                            animatable,
                            bakedModel,
                            renderType,
                            bufferSource,
                            baseBuffer,
                            partialTicks,
                            packedLight,
                            packedOverlay
                    );
                }

                if (renderType != null && baseBuffer != null) {
                    PoseStack bindStack = new PoseStack();
                    for (GeoBone top : bakedModel.topLevelBones()) {
                        GeckoAttachmentCore.replayBoneLayers(top, semanticRig, armature,
                                poseMatrices, poseStack, bindStack, ADAPTER,
                                (bone, stack) -> {
                                    for (GeoRenderLayer layer : layers) {
                                        layer.renderForBone(stack, animatable, bone, renderType,
                                                bufferSource, baseBuffer, partialTicks,
                                                packedLight, packedOverlay);
                                    }
                                });
                    }
                }
            }

            Pose efPose = entityPatch.getAnimator().getPose(partialTicks);
            for (GeoBone top : bakedModel.topLevelBones()) {
                GeckoAttachmentCore.syncPostRenderAnchors(top, -1, semanticRig,
                        armature, efPose, ADAPTER);
            }
        } catch (Throwable throwable) {
            if (WARNED_RENDERERS.add(sourceRenderer.getClass())) {
                LOGGER.warn(
                        "EFPI could not bridge Gecko attachment rendering for {} ({})",
                        sourceRenderer.getClass().getName(),
                        entity.getType(),
                        throwable
                );
            }
        }
    }

    private static void applyJointAnimationToSourceBone(
            GeoBone bone,
            Armature armature,
            Pose pose,
            int jointId) {
        BoneSnapshot snapshot = bone.getInitialSnapshot();
        Joint joint = armature.searchJointById(jointId);
        if (snapshot == null || joint == null) {
            return;
        }

        JointTransform transform = pose.orElseEmpty(joint.getName());
        Vec3f translation = transform.translation();
        Vec3f scale = transform.scale();
        float[] rotation = GeckoAttachmentMath.quaternionToZyxEuler(transform.rotation());

        bone.setPosX(snapshot.getOffsetX() - translation.x * 16.0F);
        bone.setPosY(snapshot.getOffsetY() + translation.y * 16.0F);
        bone.setPosZ(snapshot.getOffsetZ() + translation.z * 16.0F);

        bone.setRotX(snapshot.getRotX() - rotation[0]);
        bone.setRotY(snapshot.getRotY() - rotation[1]);
        bone.setRotZ(snapshot.getRotZ() - rotation[2]);

        bone.setScaleX(snapshot.getScaleX() * scale.x);
        bone.setScaleY(snapshot.getScaleY() * scale.y);
        bone.setScaleZ(snapshot.getScaleZ() * scale.z);
    }

    private static void resetBoneToInitial(GeoBone bone) {
        if (bone == null) {
            return;
        }

        BoneSnapshot snapshot = bone.getInitialSnapshot();
        if (snapshot != null) {
            bone.setPosX(snapshot.getOffsetX());
            bone.setPosY(snapshot.getOffsetY());
            bone.setPosZ(snapshot.getOffsetZ());
            bone.setRotX(snapshot.getRotX());
            bone.setRotY(snapshot.getRotY());
            bone.setRotZ(snapshot.getRotZ());
            bone.setScaleX(snapshot.getScaleX());
            bone.setScaleY(snapshot.getScaleY());
            bone.setScaleZ(snapshot.getScaleZ());
        }

    }

    private GeckoAttachmentBridge() {
    }
}
