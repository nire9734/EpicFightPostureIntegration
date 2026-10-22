package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GeckoLib 4.9.x bind-pose transform path for body rig capture and mesh baking.
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

        /*
         * GeckoArmorSkinnedMeshBridge historically started armor baking with
         * stack.scale(fitScale). That is not the coordinate system in which
         * ItemArmorGeoLayer actually renders the armor. At runtime the layer is
         * already inside the source entity bone's accumulated matrix, then it
         * applies its outer axis flip + fit scale, and GeoArmorRenderer adds its
         * own canonical armor transform before traversing armor bones.
         *
         * Reconstruct that complete bind-space composition here for armor roots.
         * First cancel the baker's old origin-centered fit scale, then reproduce:
         *
         *   sourceBoneBind
         *   * scale(-1,-1,1)        // ItemArmorGeoLayer
         *   * fitScale              // prepModelPartForRender
         *   * translate(0,1.5,0)    // GeoArmorRenderer.actuallyRender
         *   * scale(-1,-1,1)
         *
         * This keeps body/ordinary Gecko mesh baking unchanged and only affects
         * the temporary armor conversion scope.
         */
        NeoForgeGeckoArmorBakeTransformContext.State armor =
                NeoForgeGeckoArmorBakeTransformContext.current();
        if (armor != null && bone != null && bone.getParent() == null) {
            float sx = armor.scaleX();
            float sy = armor.scaleY();
            float sz = armor.scaleZ();

            stack.scale(1.0F / sx, 1.0F / sy, 1.0F / sz);
            applyInitialAbsolute(stack, armor.sourceBone());
            stack.scale(-1.0F, -1.0F, 1.0F);
            stack.scale(sx, sy, sz);
            stack.translate(0.0D, 24.0D / 16.0D, 0.0D);
            stack.scale(-1.0F, -1.0F, 1.0F);
        }

        RenderUtil.prepMatrixForBone(stack, bone);
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
        RenderUtil.translateToPivotPoint(stack, cube);
        RenderUtil.rotateMatrixAroundCube(stack, cube);
        RenderUtil.translateAwayFromPivotPoint(stack, cube);
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

    /** Applies the source bone's complete initial parent chain without mutation. */
    private static void applyInitialAbsolute(PoseStack stack, GeoBone sourceBone) {
        if (sourceBone == null) {
            return;
        }

        List<GeoBone> chain = new ArrayList<>();
        GeoBone cursor = sourceBone;
        while (cursor != null) {
            chain.add(cursor);
            cursor = cursor.getParent();
        }
        Collections.reverse(chain);

        for (GeoBone bone : chain) {
            applyInitialBone(stack, bone);
        }
    }

    /** Mirrors RenderUtil.prepMatrixForBone using the immutable initial snapshot. */
    private static void applyInitialBone(PoseStack stack, GeoBone bone) {
        BoneSnapshot snapshot = bone.getInitialSnapshot();

        float posX = snapshot != null ? snapshot.getOffsetX() : bone.getPosX();
        float posY = snapshot != null ? snapshot.getOffsetY() : bone.getPosY();
        float posZ = snapshot != null ? snapshot.getOffsetZ() : bone.getPosZ();
        float rotX = snapshot != null ? snapshot.getRotX() : bone.getRotX();
        float rotY = snapshot != null ? snapshot.getRotY() : bone.getRotY();
        float rotZ = snapshot != null ? snapshot.getRotZ() : bone.getRotZ();
        float scaleX = snapshot != null ? snapshot.getScaleX() : bone.getScaleX();
        float scaleY = snapshot != null ? snapshot.getScaleY() : bone.getScaleY();
        float scaleZ = snapshot != null ? snapshot.getScaleZ() : bone.getScaleZ();

        stack.translate(-posX / 16.0F, posY / 16.0F, posZ / 16.0F);
        stack.translate(
                bone.getPivotX() / 16.0F,
                bone.getPivotY() / 16.0F,
                bone.getPivotZ() / 16.0F
        );

        if (rotZ != 0.0F) {
            stack.mulPose(Axis.ZP.rotation(rotZ));
        }
        if (rotY != 0.0F) {
            stack.mulPose(Axis.YP.rotation(rotY));
        }
        if (rotX != 0.0F) {
            stack.mulPose(Axis.XP.rotation(rotX));
        }

        stack.scale(scaleX, scaleY, scaleZ);
        stack.translate(
                -bone.getPivotX() / 16.0F,
                -bone.getPivotY() / 16.0F,
                -bone.getPivotZ() / 16.0F
        );
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
