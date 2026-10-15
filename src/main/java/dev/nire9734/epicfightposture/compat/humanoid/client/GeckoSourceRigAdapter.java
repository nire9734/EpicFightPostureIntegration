package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSkeletonResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SemanticJointCandidate;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeHeuristics;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidModelResolution;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSourceBinding;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Converts a GeckoLib bind rig into the framework-neutral EFPI rig representation. */
public final class GeckoSourceRigAdapter {
    private static final float SCALE_EPSILON = 1.0E-5F;
    private static final float GEOMETRY_EPSILON = 1.0E-5F;

    public static Capture capture(BakedGeoModel model, SkeletonKey key) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");
        if (model.topLevelBones() == null || model.topLevelBones().isEmpty()) {
            throw new IllegalArgumentException("Gecko model has no top-level bones");
        }

        GeckoBindPoseTransforms.resetToInitial(model);

        LinkedHashMap<String, SourceNodeSnapshot> nodes = new LinkedHashMap<>();
        LinkedHashMap<String, GeoBone> sourceNodes = new LinkedHashMap<>();
        BoundsAccumulator geometryBounds = new BoundsAccumulator();
        BoundsAccumulator pivotBounds = new BoundsAccumulator();
        boolean[] nonUniformScale = {false};
        PoseStack stack = new PoseStack();

        int rootIndex = 0;
        for (GeoBone root : model.topLevelBones()) {
            captureBone(
                    root,
                    null,
                    "r" + rootIndex,
                    List.of(root.getName()),
                    false,
                    stack,
                    nodes,
                    sourceNodes,
                    geometryBounds,
                    pivotBounds,
                    nonUniformScale
            );
            rootIndex++;
        }

        BodyBounds bounds = geometryBounds.toBounds()
                .or(() -> pivotBounds.toBounds())
                .orElseThrow(() -> new IllegalArgumentException("Gecko model produced no finite bind-space bounds"));

        return new Capture(
                new SourceRigSnapshot(key, nodes, bounds, nonUniformScale[0]),
                Map.copyOf(sourceNodes)
        );
    }

    public static HumanoidModelResolution<GeoBone> resolve(
            BakedGeoModel model,
            SkeletonKey key,
            HumanoidSemanticAnalyzer analyzer) {
        Capture capture = capture(model, key);
        HumanoidSemanticAnalysis analysis = analyzer.analyze(capture.snapshot());
        var descriptor = HumanoidSkeletonResolver.resolve(capture.snapshot(), analysis);

        EnumMap<HumanoidJoint, GeoBone> semanticSources = new EnumMap<>(HumanoidJoint.class);
        for (Map.Entry<HumanoidJoint, SemanticJointCandidate> entry : analysis.candidates().entrySet()) {
            entry.getValue().sourceNodeId().ifPresent(sourceId -> {
                GeoBone source = capture.sourceNodes().get(sourceId);
                if (source != null) {
                    semanticSources.put(entry.getKey(), source);
                }
            });
        }

        HumanoidSourceBinding<GeoBone> binding = joint -> Optional.ofNullable(semanticSources.get(joint));
        return new HumanoidModelResolution<>(descriptor, binding);
    }

    private static void captureBone(
            GeoBone bone,
            String parentId,
            String nodeId,
            List<String> path,
            boolean inheritedAttachment,
            PoseStack stack,
            Map<String, SourceNodeSnapshot> nodes,
            Map<String, GeoBone> sourceNodes,
            BoundsAccumulator globalGeometryBounds,
            BoundsAccumulator globalPivotBounds,
            boolean[] nonUniformScale) {
        if (bone == null) {
            return;
        }

        boolean attachment = inheritedAttachment || SourceNodeHeuristics.pathHasAttachment(path);

        float scaleX = bone.getScaleX();
        float scaleY = bone.getScaleY();
        float scaleZ = bone.getScaleZ();
        if (Math.abs(scaleX - scaleY) > SCALE_EPSILON
                || Math.abs(scaleX - scaleZ) > SCALE_EPSILON
                || Math.abs(scaleY - scaleZ) > SCALE_EPSILON) {
            nonUniformScale[0] = true;
        }

        GeckoBindPoseTransforms.pushBone(stack, bone);
        SkeletonVec3 pivot = GeckoBindPoseTransforms.bonePivot(stack, bone);
        if (!attachment) {
            globalPivotBounds.include(pivot);
        }

        RigidTransform bindTransform = new RigidTransform(
                pivot,
                GeckoBindPoseTransforms.boneRotation(stack)
        );

        BoundsAccumulator ownGeometry = new BoundsAccumulator();
        boolean renderable = false;
        if (!attachment && bone.getCubes() != null) {
            for (GeoCube cube : bone.getCubes()) {
                // Gecko entity models may contain UV-less inflated cubes solely
                // for ItemArmorGeoLayer's fitting math. They still need to exist
                // as source bones/anchors, but must not affect body bounds or
                // semantic geometry inference.
                if (GeckoGeometryHeuristics.isLayoutReferenceCube(bone, cube)) {
                    continue;
                }
                CubeCapture cubeCapture = captureCube(stack, cube);
                cubeCapture.bounds().ifPresent(bounds -> {
                    ownGeometry.include(bounds);
                    globalGeometryBounds.include(bounds);
                });
                renderable |= cubeCapture.renderable();
            }
        }

        SourceNodeSnapshot snapshot = new SourceNodeSnapshot(
                nodeId,
                bone.getName(),
                Optional.ofNullable(parentId),
                bindTransform,
                ownGeometry.toBounds(),
                renderable
        );
        nodes.put(nodeId, snapshot);
        sourceNodes.put(nodeId, bone);

        if (bone.getChildBones() != null) {
            int childIndex = 0;
            for (GeoBone child : bone.getChildBones()) {
                List<String> childPath = new ArrayList<>(path);
                childPath.add(child.getName());
                captureBone(
                        child,
                        nodeId,
                        nodeId + "/" + childIndex,
                        List.copyOf(childPath),
                        attachment,
                        stack,
                        nodes,
                        sourceNodes,
                        globalGeometryBounds,
                        globalPivotBounds,
                        nonUniformScale
                );
                childIndex++;
            }
        }

        stack.popPose();
    }

    private static CubeCapture captureCube(PoseStack boneStack, GeoCube cube) {
        if (cube == null || cube.quads() == null || cube.quads().length == 0) {
            return CubeCapture.empty();
        }

        GeckoBindPoseTransforms.pushCube(boneStack, cube);

        BoundsAccumulator bounds = new BoundsAccumulator();
        for (GeoQuad quad : cube.quads()) {
            if (quad == null || quad.vertices() == null) {
                continue;
            }
            for (GeoVertex vertex : quad.vertices()) {
                if (vertex == null || vertex.position() == null) {
                    continue;
                }
                SkeletonVec3 position = GeckoBindPoseTransforms.transformPosition(
                        boneStack,
                        new Vector3f(vertex.position())
                );
                if (Float.isFinite(position.x())
                        && Float.isFinite(position.y())
                        && Float.isFinite(position.z())) {
                    bounds.include(position);
                }
            }
        }

        boneStack.popPose();
        Optional<BodyBounds> result = bounds.toBounds();
        boolean renderable = result
                .map(value -> value.width() > GEOMETRY_EPSILON
                        || value.height() > GEOMETRY_EPSILON
                        || value.depth() > GEOMETRY_EPSILON)
                .orElse(false);
        return new CubeCapture(result, renderable);
    }

    public record Capture(SourceRigSnapshot snapshot, Map<String, GeoBone> sourceNodes) {
        public Capture {
            Objects.requireNonNull(snapshot, "snapshot");
            sourceNodes = sourceNodes == null ? Map.of() : Map.copyOf(sourceNodes);
        }
    }

    private record CubeCapture(Optional<BodyBounds> bounds, boolean renderable) {
        static CubeCapture empty() {
            return new CubeCapture(Optional.empty(), false);
        }
    }

    private static final class BoundsAccumulator {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private boolean any;

        void include(SkeletonVec3 point) {
            any = true;
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
            maxZ = Math.max(maxZ, point.z());
        }

        void include(BodyBounds bounds) {
            include(bounds.min());
            include(bounds.max());
        }

        Optional<BodyBounds> toBounds() {
            if (!any) {
                return Optional.empty();
            }
            return Optional.of(new BodyBounds(
                    new SkeletonVec3(minX, minY, minZ),
                    new SkeletonVec3(maxX, maxY, maxZ)
            ));
        }
    }

    private GeckoSourceRigAdapter() {
    }
}
