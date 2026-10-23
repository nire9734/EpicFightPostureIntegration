package dev.nire9734.epicfightposture.compat.humanoid.client;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Identical bind-space math for both GeckoLib API generations. Only the
 * renamed RenderUtil methods and BoneSnapshot package are loader adapters.
 */
public final class GeckoBindPoseCore {
    private static final float NORMAL_EPSILON = 1.0E-8F;

    public record InitialSnapshot(float posX, float posY, float posZ,
                                  float rotX, float rotY, float rotZ,
                                  float scaleX, float scaleY, float scaleZ) { }

    public interface Platform {
        InitialSnapshot initialSnapshot(GeoBone bone);
        void prepareBone(PoseStack stack, GeoBone bone);
        void translateCubeToPivot(PoseStack stack, GeoCube cube);
        void rotateCube(PoseStack stack, GeoCube cube);
        void translateCubeAwayFromPivot(PoseStack stack, GeoCube cube);
    }

    private final Platform platform;

    public GeckoBindPoseCore(Platform platform) {
        this.platform = platform;
    }

    public GeckoBindPoseOperations operations() {
        return new GeckoBindPoseOperations(this::resetToInitial, this::pushBone,
                this::bonePivot, this::boneRotation, this::pushCube,
                this::transformPosition, this::transformNormal);
    }

    private void resetToInitial(BakedGeoModel model) {
        if (model == null || model.topLevelBones() == null) return;
        for (GeoBone root : model.topLevelBones()) resetToInitial(root);
    }

    private void resetToInitial(GeoBone bone) {
        if (bone == null) return;
        InitialSnapshot snapshot = platform.initialSnapshot(bone);
        if (snapshot != null) {
            bone.setPosX(snapshot.posX());
            bone.setPosY(snapshot.posY());
            bone.setPosZ(snapshot.posZ());
            bone.setRotX(snapshot.rotX());
            bone.setRotY(snapshot.rotY());
            bone.setRotZ(snapshot.rotZ());
            bone.setScaleX(snapshot.scaleX());
            bone.setScaleY(snapshot.scaleY());
            bone.setScaleZ(snapshot.scaleZ());
        }
        if (bone.getChildBones() != null) {
            for (GeoBone child : bone.getChildBones()) resetToInitial(child);
        }
    }

    private void pushBone(PoseStack stack, GeoBone bone) {
        stack.pushPose();
        // GeoArmorRenderer renders an armor root after its source-bone bind,
        // outer axis flip, fit scale, and canonical 1.5-block translation.
        GeckoArmorBakeTransformContext.State armor = GeckoArmorBakeTransformContext.current();
        if (armor != null && bone != null && bone.getParent() == null) {
            float sx = armor.scaleX(), sy = armor.scaleY(), sz = armor.scaleZ();
            stack.scale(1.0F / sx, 1.0F / sy, 1.0F / sz);
            applyInitialAbsolute(stack, armor.sourceBone());
            stack.scale(-1.0F, -1.0F, 1.0F);
            stack.scale(sx, sy, sz);
            stack.translate(0.0D, 24.0D / 16.0D, 0.0D);
            stack.scale(-1.0F, -1.0F, 1.0F);
        }
        platform.prepareBone(stack, bone);
    }

    private SkeletonVec3 bonePivot(PoseStack stack, GeoBone bone) {
        return transformPosition(stack, new Vector3f(
                bone.getPivotX() / 16.0F,
                bone.getPivotY() / 16.0F,
                bone.getPivotZ() / 16.0F));
    }

    private SkeletonQuat boneRotation(PoseStack stack) {
        Quaternionf q = new Matrix4f(stack.last().pose()).getUnnormalizedRotation(new Quaternionf());
        return new SkeletonQuat(q.x(), q.y(), q.z(), q.w());
    }

    private void pushCube(PoseStack stack, GeoCube cube) {
        stack.pushPose();
        platform.translateCubeToPivot(stack, cube);
        platform.rotateCube(stack, cube);
        platform.translateCubeAwayFromPivot(stack, cube);
    }

    private SkeletonVec3 transformPosition(PoseStack stack, Vector3f position) {
        Vector4f transformed = new Vector4f(position, 1.0F).mul(stack.last().pose());
        return new SkeletonVec3(transformed.x(), transformed.y(), transformed.z());
    }

    private Vector3f transformNormal(PoseStack stack, Vector3f normal) {
        Vector3f transformed = new Vector3f(normal).mul(stack.last().normal());
        if (transformed.lengthSquared() > NORMAL_EPSILON) transformed.normalize();
        return transformed;
    }

    private void applyInitialAbsolute(PoseStack stack, GeoBone sourceBone) {
        if (sourceBone == null) return;
        List<GeoBone> chain = new ArrayList<>();
        for (GeoBone node = sourceBone; node != null; node = node.getParent()) chain.add(node);
        Collections.reverse(chain);
        for (GeoBone node : chain) applyInitialBone(stack, node);
    }

    /** Reproduces Gecko's initial-pose transform without mutating its GeoBones. */
    private void applyInitialBone(PoseStack stack, GeoBone bone) {
        InitialSnapshot s = platform.initialSnapshot(bone);
        float px = s == null ? bone.getPosX() : s.posX();
        float py = s == null ? bone.getPosY() : s.posY();
        float pz = s == null ? bone.getPosZ() : s.posZ();
        float rx = s == null ? bone.getRotX() : s.rotX();
        float ry = s == null ? bone.getRotY() : s.rotY();
        float rz = s == null ? bone.getRotZ() : s.rotZ();
        float sx = s == null ? bone.getScaleX() : s.scaleX();
        float sy = s == null ? bone.getScaleY() : s.scaleY();
        float sz = s == null ? bone.getScaleZ() : s.scaleZ();

        stack.translate(-px / 16.0F, py / 16.0F, pz / 16.0F);
        stack.translate(bone.getPivotX() / 16.0F, bone.getPivotY() / 16.0F,
                bone.getPivotZ() / 16.0F);
        if (rz != 0.0F) stack.mulPose(Axis.ZP.rotation(rz));
        if (ry != 0.0F) stack.mulPose(Axis.YP.rotation(ry));
        if (rx != 0.0F) stack.mulPose(Axis.XP.rotation(rx));
        stack.scale(sx, sy, sz);
        stack.translate(-bone.getPivotX() / 16.0F, -bone.getPivotY() / 16.0F,
                -bone.getPivotZ() / 16.0F);
    }
}
