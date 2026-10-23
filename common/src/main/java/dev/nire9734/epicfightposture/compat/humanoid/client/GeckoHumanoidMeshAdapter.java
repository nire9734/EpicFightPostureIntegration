package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeHeuristics;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.MeshPartDefinition;
import yesman.epicfight.api.client.model.SingleGroupVertexBuilder;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec2f;
import yesman.epicfight.api.utils.math.Vec3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One-time GeckoLib bind model -> Epic Fight skinned mesh conversion using the
 * same framework-neutral semantic analysis and skin weighting as other backends.
 */
public final class GeckoHumanoidMeshAdapter {
    private static final MeshPartDefinition PART = new RuntimeGeoPart("gecko_humanoid");

    public static Result analyseAndBake(
            BakedGeoModel model, SkeletonKey key, GeckoBindPoseOperations operations) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operations, "operations");

        try {
            GeckoResolvedHumanoidRig resolved = GeckoResolvedHumanoidRig.resolve(model, key, operations);
            HumanoidSkeletonDescriptor descriptor = resolved.descriptor();
            HumanoidSkinWeightResolver weights = resolved.weights();

            operations.resetToInitial(model);

            List<SingleGroupVertexBuilder> vertices = new ArrayList<>();
            Map<MeshPartDefinition, IntList> indices = new LinkedHashMap<>();
            PoseStack stack = new PoseStack();

            int rootIndex = 0;
            for (GeoBone root : model.topLevelBones()) {
                bakeBone(
                        root,
                        "r" + rootIndex,
                        List.of(root.getName()),
                        stack,
                        weights,
                        vertices,
                        indices,
                        false,
                        operations
                );
                rootIndex++;
            }

            SkinnedMesh mesh = vertices.isEmpty()
                    ? null
                    : SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
            return mesh == null
                    ? Result.unsupported("mesh bake produced no body vertices")
                    : Result.supported(descriptor, mesh);
        } catch (Throwable throwable) {
            return Result.unsupported(
                    "shared Gecko humanoid pipeline failed: "
                            + throwable.getClass().getSimpleName()
                            + (throwable.getMessage() == null ? "" : " (" + throwable.getMessage() + ")")
            );
        }
    }

    private static void bakeBone(
            GeoBone bone,
            String nodeId,
            List<String> path,
            PoseStack stack,
            HumanoidSkinWeightResolver weights,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices,
            boolean inheritedAttachment,
            GeckoBindPoseOperations operations) {
        if (bone == null) {
            return;
        }

        boolean attachment = inheritedAttachment || SourceNodeHeuristics.pathHasAttachment(path);

        operations.pushBone(stack, bone);

        if (!bone.isHidden() && !attachment && bone.getCubes() != null) {
            for (GeoCube cube : bone.getCubes()) {
                // Gecko armor fitting/reference cubes are deliberately present in
                // some entity GeoModels but are not body presentation geometry.
                // Baking them here makes their empty UVs cover the real entity and
                // can also occlude the actual ItemArmorGeoLayer result.
                if (GeckoGeometryHeuristics.isLayoutReferenceCube(bone, cube)) {
                    continue;
                }
                bakeCube(nodeId, cube, stack, weights, vertices, indices, operations);
            }
        }

        if (!bone.isHidingChildren() && bone.getChildBones() != null) {
            int childIndex = 0;
            for (GeoBone child : bone.getChildBones()) {
                List<String> childPath = new ArrayList<>(path);
                childPath.add(child.getName());
                bakeBone(
                        child,
                        nodeId + "/" + childIndex,
                        List.copyOf(childPath),
                        stack,
                        weights,
                        vertices,
                        indices,
                        attachment,
                        operations
                );
                childIndex++;
            }
        }

        stack.popPose();
    }

    private static void bakeCube(
            String nodeId,
            GeoCube cube,
            PoseStack stack,
            HumanoidSkinWeightResolver weights,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices,
            GeckoBindPoseOperations operations) {
        if (cube == null || cube.quads() == null) {
            return;
        }

        operations.pushCube(stack, cube);

        for (GeoQuad quad : cube.quads()) {
            if (!isRenderableQuad(quad)) {
                continue;
            }

            Vector3f normal = operations.transformNormal(stack, quad.normal());

            int first = vertices.size();
            for (GeoVertex vertex : quad.vertices()) {
                SkeletonVec3 position = operations.transformPosition(
                        stack,
                        new Vector3f(vertex.position())
                );
                HumanoidSkinWeightResolver.SkinWeights skin = weights.weights(nodeId, position);

                vertices.add(new SingleGroupVertexBuilder()
                        .setPosition(new Vec3f(position.x(), position.y(), position.z()))
                        .setNormal(new Vec3f(normal.x(), normal.y(), normal.z()))
                        .setTextureCoordinate(new Vec2f(vertex.texU(), vertex.texV()))
                        .setEffectiveJointIDs(new Vec3f(skin.firstJoint(), skin.secondJoint(), 0.0F))
                        .setEffectiveJointWeights(new Vec3f(skin.firstWeight(), skin.secondWeight(), 0.0F))
                        .setEffectiveJointNumber(skin.count()));
            }
            triangulateQuad(indices, first);
        }

        stack.popPose();
    }

    private static boolean isRenderableQuad(GeoQuad quad) {
        if (quad == null || quad.normal() == null) {
            return false;
        }
        GeoVertex[] vertices = quad.vertices();
        if (vertices == null || vertices.length != 4) {
            return false;
        }
        for (GeoVertex vertex : vertices) {
            if (vertex == null || vertex.position() == null) {
                return false;
            }
        }
        return true;
    }

    private static void triangulateQuad(Map<MeshPartDefinition, IntList> indices, int first) {
        IntList list = indices.computeIfAbsent(PART, ignored -> new IntArrayList());
        add3(list, first);
        add3(list, first + 1);
        add3(list, first + 3);
        add3(list, first + 3);
        add3(list, first + 1);
        add3(list, first + 2);
    }

    private static void add3(IntList list, int value) {
        list.add(value);
        list.add(value);
        list.add(value);
    }

    public record Result(
            boolean supported,
            HumanoidSkeletonDescriptor descriptor,
            SkinnedMesh mesh,
            String reason) {
        static Result supported(HumanoidSkeletonDescriptor descriptor, SkinnedMesh mesh) {
            return new Result(true, descriptor, mesh, "ok");
        }

        static Result unsupported(String reason) {
            return new Result(false, null, null, reason);
        }
    }

    private record RuntimeGeoPart(String partName) implements MeshPartDefinition {
        @Override
        public Mesh.RenderProperties renderProperties() {
            return null;
        }

        @Override
        public Supplier<OpenMatrix4f> getModelPartAnimationProvider() {
            return () -> null;
        }
    }

    private GeckoHumanoidMeshAdapter() {
    }
}
