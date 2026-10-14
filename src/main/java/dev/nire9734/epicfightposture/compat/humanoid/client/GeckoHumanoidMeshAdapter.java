package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.ConservativeHumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSkeletonResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeHeuristics;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.util.RenderUtils;
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

    public static Result analyseAndBake(BakedGeoModel model, SkeletonKey key) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");

        try {
            GeckoSourceRigAdapter.Capture capture = GeckoSourceRigAdapter.capture(model, key);
            HumanoidSemanticAnalysis analysis =
                    ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(capture.snapshot());
            HumanoidSkeletonDescriptor descriptor =
                    HumanoidSkeletonResolver.resolve(capture.snapshot(), analysis);
            HumanoidSkinWeightResolver weights =
                    new HumanoidSkinWeightResolver(capture.snapshot(), analysis, descriptor);

            for (GeoBone root : model.topLevelBones()) {
                resetToInitial(root);
            }

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
                        false
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
            boolean inheritedAttachment) {
        if (bone == null) {
            return;
        }

        boolean attachment = inheritedAttachment || SourceNodeHeuristics.pathHasAttachment(path);

        stack.pushPose();
        RenderUtils.prepMatrixForBone(stack, bone);

        if (!bone.isHidden() && !attachment && bone.getCubes() != null) {
            for (GeoCube cube : bone.getCubes()) {
                bakeCube(nodeId, cube, stack, weights, vertices, indices);
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
                        attachment
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
            Map<MeshPartDefinition, IntList> indices) {
        if (cube == null || cube.quads() == null) {
            return;
        }

        stack.pushPose();
        RenderUtils.translateToPivotPoint(stack, cube);
        RenderUtils.rotateMatrixAroundCube(stack, cube);
        RenderUtils.translateAwayFromPivotPoint(stack, cube);

        for (GeoQuad quad : cube.quads()) {
            if (!isRenderableQuad(quad)) {
                continue;
            }

            Vector3f normal = new Vector3f(quad.normal());
            normal.mul(stack.last().normal());
            if (normal.lengthSquared() > 1.0E-8F) {
                normal.normalize();
            }

            int first = vertices.size();
            for (GeoVertex vertex : quad.vertices()) {
                Vector4f pos = new Vector4f(vertex.position(), 1.0F);
                pos.mul(stack.last().pose());
                SkeletonVec3 position = new SkeletonVec3(pos.x(), pos.y(), pos.z());
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
