package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

/** Shared Gecko attachment traversal and pose composition. */
public final class GeckoAttachmentCore {
    public interface BoneAdapter {
        void resetBone(GeoBone bone);
        void prepareMatrix(PoseStack bindStack, GeoBone bone);
        void multiplyPose(PoseStack poseStack, Matrix4f attachment);
        void applyJointAnimation(GeoBone bone, Armature armature, Pose pose, int jointId);
    }

    @FunctionalInterface
    public interface LayerRenderer {
        void render(GeoBone bone, PoseStack poseStack);
    }

    public static void resetTree(GeoBone bone, BoneAdapter adapter) {
        if (bone == null) return;
        adapter.resetBone(bone);
        for (GeoBone child : bone.getChildBones()) {
            resetTree(child, adapter);
        }
    }

    public static void replayBoneLayers(GeoBone bone, GeckoResolvedHumanoidRig rig,
                                        Armature armature, OpenMatrix4f[] matrices,
                                        PoseStack poseStack, PoseStack bindStack,
                                        BoneAdapter adapter, LayerRenderer renderLayers) {
        if (bone == null) return;
        int jointId = rig.deformationJointId(bone);

        bindStack.pushPose();
        try {
            adapter.prepareMatrix(bindStack, bone);
            Matrix4f geoBind = new Matrix4f(bindStack.last().pose());
            Matrix4f attachment = GeckoAttachmentMath.attachmentMatrix(
                    armature, matrices, jointId, geoBind);
            poseStack.pushPose();
            try {
                adapter.multiplyPose(poseStack, attachment);
                renderLayers.render(bone, poseStack);
            } finally {
                poseStack.popPose();
            }

            if (!bone.isHidingChildren()) {
                for (GeoBone child : bone.getChildBones()) {
                    replayBoneLayers(child, rig, armature, matrices, poseStack, bindStack,
                            adapter, renderLayers);
                }
            }
        } finally {
            bindStack.popPose();
        }
    }

    public static void syncPostRenderAnchors(GeoBone bone, int inheritedJoint,
                                             GeckoResolvedHumanoidRig rig, Armature armature,
                                             Pose pose, BoneAdapter adapter) {
        if (bone == null) return;
        int jointId = rig.deformationJointId(bone);
        if (inheritedJoint < 0 || jointId != inheritedJoint) {
            adapter.applyJointAnimation(bone, armature, pose, jointId);
        }
        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                syncPostRenderAnchors(child, jointId, rig, armature, pose, adapter);
            }
        }
    }

    private GeckoAttachmentCore() { }
}
