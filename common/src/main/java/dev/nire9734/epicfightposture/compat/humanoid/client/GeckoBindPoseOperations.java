package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** Version-specific GeckoLib pose operations consumed by the shared rig and mesh pipelines. */
public record GeckoBindPoseOperations(
        Consumer<BakedGeoModel> reset,
        BiConsumer<PoseStack, GeoBone> bone,
        BiFunction<PoseStack, GeoBone, SkeletonVec3> pivot,
        Function<PoseStack, SkeletonQuat> rotation,
        BiConsumer<PoseStack, GeoCube> cube,
        BiFunction<PoseStack, Vector3f, SkeletonVec3> position,
        BiFunction<PoseStack, Vector3f, Vector3f> normal) {

    public void resetToInitial(BakedGeoModel model) {
        reset.accept(model);
    }

    public void pushBone(PoseStack stack, GeoBone geoBone) {
        bone.accept(stack, geoBone);
    }

    public SkeletonVec3 bonePivot(PoseStack stack, GeoBone geoBone) {
        return pivot.apply(stack, geoBone);
    }

    public SkeletonQuat boneRotation(PoseStack stack) {
        return rotation.apply(stack);
    }

    public void pushCube(PoseStack stack, GeoCube geoCube) {
        cube.accept(stack, geoCube);
    }

    public SkeletonVec3 transformPosition(PoseStack stack, Vector3f vector) {
        return position.apply(stack, vector);
    }

    public Vector3f transformNormal(PoseStack stack, Vector3f vector) {
        return normal.apply(stack, vector);
    }
}
