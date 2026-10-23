package dev.nire9734.epicfightposture.neoforge.compat.models;

import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoResolvedHumanoidRig;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoAttachmentMath;

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
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;
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
final class NeoForgeGeckoAttachmentBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Class<?>> WARNED_RENDERERS = ConcurrentHashMap.newKeySet();

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
                resetTreeToInitial(top);
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
                        replayBoneLayers(
                                top,
                                semanticRig,
                                armature,
                                poseMatrices,
                                layers,
                                animatable,
                                renderType,
                                bufferSource,
                                baseBuffer,
                                poseStack,
                                bindStack,
                                packedLight,
                                packedOverlay,
                                partialTicks
                        );
                    }
                }
            }

            Pose efPose = entityPatch.getAnimator().getPose(partialTicks);
            for (GeoBone top : bakedModel.topLevelBones()) {
                syncPostRenderAnchors(top, -1, semanticRig, armature, efPose);
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

    private static void replayBoneLayers(
            GeoBone bone,
            GeckoResolvedHumanoidRig semanticRig,
            Armature armature,
            OpenMatrix4f[] poseMatrices,
            List<GeoRenderLayer> layers,
            GeoAnimatable animatable,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer baseBuffer,
            PoseStack poseStack,
            PoseStack bindStack,
            int packedLight,
            int packedOverlay,
            float partialTicks) {
        if (bone == null) {
            return;
        }

        int jointId = semanticRig.deformationJointId(bone);

        bindStack.pushPose();
        RenderUtil.prepMatrixForBone(bindStack, bone);
        Matrix4f geoBind = new Matrix4f(bindStack.last().pose());

        Matrix4f attachment = GeckoAttachmentMath.attachmentMatrix(
                armature, poseMatrices, jointId, geoBind);

        poseStack.pushPose();
        poseStack.mulPose(attachment);

        for (GeoRenderLayer layer : layers) {
            layer.renderForBone(
                    poseStack,
                    animatable,
                    bone,
                    renderType,
                    bufferSource,
                    baseBuffer,
                    partialTicks,
                    packedLight,
                    packedOverlay
            );
        }

        poseStack.popPose();

        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                replayBoneLayers(
                        child,
                        semanticRig,
                        armature,
                        poseMatrices,
                        layers,
                        animatable,
                        renderType,
                        bufferSource,
                        baseBuffer,
                        poseStack,
                        bindStack,
                        packedLight,
                        packedOverlay,
                        partialTicks
                );
            }
        }

        bindStack.popPose();
    }

    private static void syncPostRenderAnchors(
            GeoBone bone,
            int inheritedJoint,
            GeckoResolvedHumanoidRig semanticRig,
            Armature armature,
            Pose efPose) {
        if (bone == null) {
            return;
        }

        int jointId = semanticRig.deformationJointId(bone);
        if (inheritedJoint < 0 || jointId != inheritedJoint) {
            applyJointAnimationToSourceBone(bone, armature, efPose, jointId);
        }

        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                syncPostRenderAnchors(child, jointId, semanticRig, armature, efPose);
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

    private static void resetTreeToInitial(GeoBone bone) {
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

        for (GeoBone child : bone.getChildBones()) {
            resetTreeToInitial(child);
        }
    }

    private NeoForgeGeckoAttachmentBridge() {
    }
}
