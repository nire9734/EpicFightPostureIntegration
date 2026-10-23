package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoBindPoseCore;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoBindPoseOperations;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.util.RenderUtil;

/** GeckoLib 4.9.x adapter for the common bind-space mesh baker. */
final class GeckoBindPoseTransforms {
    static final GeckoBindPoseOperations OPERATIONS = new GeckoBindPoseCore(
            new GeckoBindPoseCore.Platform() {
                @Override public GeckoBindPoseCore.InitialSnapshot initialSnapshot(GeoBone bone) {
                    BoneSnapshot s = bone.getInitialSnapshot();
                    return s == null ? null : new GeckoBindPoseCore.InitialSnapshot(
                            s.getOffsetX(), s.getOffsetY(), s.getOffsetZ(),
                            s.getRotX(), s.getRotY(), s.getRotZ(),
                            s.getScaleX(), s.getScaleY(), s.getScaleZ());
                }
                @Override public void prepareBone(PoseStack stack, GeoBone bone) {
                    RenderUtil.prepMatrixForBone(stack, bone);
                }
                @Override public void translateCubeToPivot(PoseStack stack, GeoCube cube) {
                    RenderUtil.translateToPivotPoint(stack, cube);
                }
                @Override public void rotateCube(PoseStack stack, GeoCube cube) {
                    RenderUtil.rotateMatrixAroundCube(stack, cube);
                }
                @Override public void translateCubeAwayFromPivot(PoseStack stack, GeoCube cube) {
                    RenderUtil.translateAwayFromPivotPoint(stack, cube);
                }
            }).operations();

    private GeckoBindPoseTransforms() { }
}
