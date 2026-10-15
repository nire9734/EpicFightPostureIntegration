package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.ConservativeHumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSkeletonResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SemanticJointCandidate;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
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
 * Gecko bone-name heuristics. The same {@link ConservativeHumanoidSemanticAnalyzer}
 * and {@link HumanoidSkinWeightResolver} that baked the body are run once per
 * baked GeoModel and cached. Direct semantic source anchors use their canonical
 * deformation joint; decoration/attachment descendants select the dominant joint
 * from the shared spatial weight resolver. This keeps shoulder carriers, one-bone
 * limbs and inferred wrists/knees consistent with the body conversion.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class GeckoAttachmentBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Class<?>> WARNED_RENDERERS = ConcurrentHashMap.newKeySet();

    /** Weak model keys naturally invalidate on Gecko resource reload. */
    private static final Map<BakedGeoModel, SemanticRig> SEMANTIC_RIGS =
            Collections.synchronizedMap(new WeakHashMap<>());

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

            SemanticRig semanticRig = semanticRig(entity, geoModel, modelLocation, bakedModel);

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

    private static SemanticRig semanticRig(
            LivingEntity entity,
            GeoModel geoModel,
            ResourceLocation modelLocation,
            BakedGeoModel bakedModel) {
        synchronized (SEMANTIC_RIGS) {
            SemanticRig cached = SEMANTIC_RIGS.get(bakedModel);
            if (cached != null) {
                return cached;
            }

            ResourceLocation entityId = EntityType.getKey(entity.getType());
            if (entityId == null) {
                throw new IllegalArgumentException("Gecko attachment entity has no registry id");
            }

            SkeletonKey key = new SkeletonKey(
                    entityId,
                    geoModel.getClass().getName() + "|" + String.valueOf(modelLocation)
            );
            GeckoSourceRigAdapter.Capture capture = GeckoSourceRigAdapter.capture(bakedModel, key);
            HumanoidSemanticAnalysis analysis =
                    ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(capture.snapshot());
            HumanoidSkeletonDescriptor descriptor =
                    HumanoidSkeletonResolver.resolve(capture.snapshot(), analysis);
            HumanoidSkinWeightResolver weights =
                    new HumanoidSkinWeightResolver(capture.snapshot(), analysis, descriptor);

            IdentityHashMap<GeoBone, String> nodeIds = new IdentityHashMap<>();
            capture.sourceNodes().forEach((sourceId, bone) -> nodeIds.put(bone, sourceId));

            IdentityHashMap<GeoBone, HumanoidJoint> directOwners = new IdentityHashMap<>();
            for (HumanoidJoint joint : HumanoidJoint.values()) {
                SemanticJointCandidate candidate = analysis.candidate(joint).orElse(null);
                if (candidate == null || candidate.origin() != JointOrigin.SOURCE) {
                    continue;
                }
                candidate.sourceNodeId().ifPresent(sourceId -> {
                    GeoBone bone = capture.sourceNodes().get(sourceId);
                    if (bone != null) {
                        directOwners.putIfAbsent(bone, joint);
                    }
                });
            }

            SemanticRig created = new SemanticRig(
                    capture.snapshot(),
                    weights,
                    nodeIds,
                    directOwners
            );
            SEMANTIC_RIGS.put(bakedModel, created);
            return created;
        }
    }

    private static void replayBoneLayers(
            GeoBone bone,
            SemanticRig semanticRig,
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
        RenderUtils.prepMatrixForBone(bindStack, bone);
        Matrix4f geoBind = new Matrix4f(bindStack.last().pose());

        Matrix4f attachment = geoBind;
        if (jointId >= 0 && jointId < poseMatrices.length && poseMatrices[jointId] != null) {
            Joint joint = armature.searchJointById(jointId);
            if (joint != null) {
                OpenMatrix4f skin = new OpenMatrix4f(poseMatrices[jointId]);
                skin.mulBack(joint.getToOrigin());
                Matrix4f skinMatrix = OpenMatrix4f.exportToMojangMatrix(skin);
                attachment = new Matrix4f(skinMatrix).mul(geoBind);
            }
        }

        poseStack.pushPose();
        poseStack.mulPoseMatrix(attachment);

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
            SemanticRig semanticRig,
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
        float[] rotation = quaternionToZyxEuler(transform.rotation());

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

    private static float[] quaternionToZyxEuler(Quaternionf source) {
        Quaternionf q = new Quaternionf(source).normalize();
        float x = q.x;
        float y = q.y;
        float z = q.z;
        float w = q.w;

        float sinXCosY = 2.0F * (w * x + y * z);
        float cosXCosY = 1.0F - 2.0F * (x * x + y * y);
        float rotX = (float)Math.atan2(sinXCosY, cosXCosY);

        float sinY = 2.0F * (w * y - z * x);
        float rotY = Math.abs(sinY) >= 1.0F
                ? (float)Math.copySign(Math.PI / 2.0, sinY)
                : (float)Math.asin(sinY);

        float sinZCosY = 2.0F * (w * z + x * y);
        float cosZCosY = 1.0F - 2.0F * (y * y + z * z);
        float rotZ = (float)Math.atan2(sinZCosY, cosZCosY);

        return new float[] {rotX, rotY, rotZ};
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

    private static final class SemanticRig {
        private final SourceRigSnapshot snapshot;
        private final HumanoidSkinWeightResolver weights;
        private final IdentityHashMap<GeoBone, String> nodeIds;
        private final IdentityHashMap<GeoBone, HumanoidJoint> directOwners;

        private SemanticRig(
                SourceRigSnapshot snapshot,
                HumanoidSkinWeightResolver weights,
                IdentityHashMap<GeoBone, String> nodeIds,
                IdentityHashMap<GeoBone, HumanoidJoint> directOwners) {
            this.snapshot = snapshot;
            this.weights = weights;
            this.nodeIds = new IdentityHashMap<>(nodeIds);
            this.directOwners = new IdentityHashMap<>(directOwners);
        }

        private int deformationJointId(GeoBone bone) {
            HumanoidJoint directOwner = directOwners.get(bone);
            if (directOwner != null) {
                return HumanoidSkinWeightResolver.deformationJointId(directOwner);
            }

            String nodeId = nodeIds.get(bone);
            if (nodeId == null) {
                return HumanoidSkinWeightResolver.ROOT;
            }

            try {
                HumanoidSkinWeightResolver.SkinWeights skin = weights.weights(
                        nodeId,
                        snapshot.node(nodeId).bindModelTransform().position()
                );
                if (skin.count() == 1 || skin.firstWeight() >= skin.secondWeight()) {
                    return skin.firstJoint();
                }
                return skin.secondJoint();
            } catch (Throwable ignored) {
                return HumanoidSkinWeightResolver.ROOT;
            }
        }
    }

    private GeckoAttachmentBridge() {
    }
}
