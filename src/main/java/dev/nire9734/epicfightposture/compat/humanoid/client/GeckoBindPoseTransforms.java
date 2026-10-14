package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Shared GeckoLib bind-pose transform path used by both rig capture and mesh baking.
 * Renderer presentation transforms intentionally do not belong here.
 */
final class GeckoBindPoseTransforms {
    private static final float NORMAL_EPSILON = 1.0E-8F;

    static void resetToInitial(BakedGeoModel model) {
        if (model == null || model.topLevelBones() == null) {
            return;
        }
        for (GeoBone root : model.topLevelBones()) {
            resetToInitial(root);
        }
    }

    static void pushBone(PoseStack stack, GeoBone bone) {
        stack.pushPose();
        RenderUtils.prepMatrixForBone(stack, bone);
    }

    static SkeletonVec3 bonePivot(PoseStack preparedBoneStack, GeoBone bone) {
        return transformPosition(
                preparedBoneStack,
                new Vector3f(
                        bone.getPivotX() / 16.0F,
                        bone.getPivotY() / 16.0F,
                        bone.getPivotZ() / 16.0F
                )
        );
    }

    static SkeletonQuat boneRotation(PoseStack preparedBoneStack) {
        Quaternionf rotation = new Matrix4f(preparedBoneStack.last().pose())
                .getUnnormalizedRotation(new Quaternionf());
        return new SkeletonQuat(rotation.x(), rotation.y(), rotation.z(), rotation.w());
    }

    static void pushCube(PoseStack stack, GeoCube cube) {
        stack.pushPose();
        RenderUtils.translateToPivotPoint(stack, cube);
        RenderUtils.rotateMatrixAroundCube(stack, cube);
        RenderUtils.translateAwayFromPivotPoint(stack, cube);
    }

    static SkeletonVec3 transformPosition(PoseStack stack, Vector3f position) {
        Vector4f transformed = new Vector4f(position, 1.0F);
        transformed.mul(stack.last().pose());
        return new SkeletonVec3(transformed.x(), transformed.y(), transformed.z());
    }

    static Vector3f transformNormal(PoseStack stack, Vector3f normal) {
        Vector3f transformed = new Vector3f(normal);
        transformed.mul(stack.last().normal());
        if (transformed.lengthSquared() > NORMAL_EPSILON) {
            transformed.normalize();
        }
        return transformed;
    }

    private static void resetToInitial(GeoBone bone) {
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
        if (bone.getChildBones() != null) {
            for (GeoBone child : bone.getChildBones()) {
                resetToInitial(child);
            }
        }
    }

    private GeckoBindPoseTransforms() {
    }
}
