package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeHeuristics;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import mod.azure.azurelib.cache.object.BakedGeoModel;
import mod.azure.azurelib.cache.object.GeoBone;
import mod.azure.azurelib.cache.object.GeoCube;
import mod.azure.azurelib.cache.object.GeoQuad;
import mod.azure.azurelib.cache.object.GeoVertex;
import mod.azure.azurelib.core.state.BoneSnapshot;
import mod.azure.azurelib.util.RenderUtils;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Converts an AzureLib 1.x bind rig into EFPI's framework-neutral source rig. */
public final class AzureSourceRigAdapter {
    private static final float SCALE_EPSILON = 1.0E-5F;
    private static final float GEOMETRY_EPSILON = 1.0E-5F;

    public static Capture capture(BakedGeoModel model, SkeletonKey key) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");
        if (model.topLevelBones() == null || model.topLevelBones().isEmpty()) {
            throw new IllegalArgumentException("Azure model has no top-level bones");
        }

        for (GeoBone root : model.topLevelBones()) {
            resetToInitial(root);
        }

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
                .orElseThrow(() -> new IllegalArgumentException("Azure model produced no finite bind-space bounds"));

        return new Capture(
                new SourceRigSnapshot(key, nodes, bounds, nonUniformScale[0]),
                Map.copyOf(sourceNodes)
        );
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
        Matrix4f parentPose = new Matrix4f(stack.last().pose());
        SkeletonVec3 pivot = transformedBonePivot(parentPose, bone);
        if (!attachment) {
            globalPivotBounds.include(pivot);
        }

        float scaleX = bone.getScaleX();
        float scaleY = bone.getScaleY();
        float scaleZ = bone.getScaleZ();
        if (Math.abs(scaleX - scaleY) > SCALE_EPSILON
                || Math.abs(scaleX - scaleZ) > SCALE_EPSILON
                || Math.abs(scaleY - scaleZ) > SCALE_EPSILON) {
            nonUniformScale[0] = true;
        }

        stack.pushPose();
        RenderUtils.prepMatrixForBone(stack, bone);

        Quaternionf sourceRotation = new Matrix4f(stack.last().pose())
                .getUnnormalizedRotation(new Quaternionf());
        RigidTransform bindTransform = new RigidTransform(
                pivot,
                new SkeletonQuat(
                        sourceRotation.x(),
                        sourceRotation.y(),
                        sourceRotation.z(),
                        sourceRotation.w()
                )
        );

        BoundsAccumulator ownGeometry = new BoundsAccumulator();
        boolean renderable = false;
        if (!attachment && bone.getCubes() != null) {
            for (GeoCube cube : bone.getCubes()) {
                CubeCapture cubeCapture = captureCube(stack, cube);
                cubeCapture.bounds().ifPresent(bounds -> {
                    ownGeometry.include(bounds);
                    globalGeometryBounds.include(bounds);
                });
                renderable |= cubeCapture.renderable();
            }
        }

        nodes.put(nodeId, new SourceNodeSnapshot(
                nodeId,
                bone.getName(),
                Optional.ofNullable(parentId),
                bindTransform,
                ownGeometry.toBounds(),
                renderable
        ));
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

    private static SkeletonVec3 transformedBonePivot(Matrix4f parentPose, GeoBone bone) {
        Vector4f pivot = new Vector4f(
                (bone.getPivotX() - bone.getPosX()) / 16.0F,
                (bone.getPivotY() + bone.getPosY()) / 16.0F,
                (bone.getPivotZ() + bone.getPosZ()) / 16.0F,
                1.0F
        );
        pivot.mul(parentPose);
        return new SkeletonVec3(pivot.x(), pivot.y(), pivot.z());
    }

    private static CubeCapture captureCube(PoseStack boneStack, GeoCube cube) {
        if (cube == null || cube.quads() == null || cube.quads().length == 0) {
            return CubeCapture.empty();
        }

        boneStack.pushPose();
        RenderUtils.translateToPivotPoint(boneStack, cube);
        RenderUtils.rotateMatrixAroundCube(boneStack, cube);
        RenderUtils.translateAwayFromPivotPoint(boneStack, cube);

        BoundsAccumulator bounds = new BoundsAccumulator();
        for (GeoQuad quad : cube.quads()) {
            if (quad == null || quad.vertices() == null) {
                continue;
            }
            for (GeoVertex vertex : quad.vertices()) {
                if (vertex == null || vertex.position() == null) {
                    continue;
                }
                Vector4f position = new Vector4f(vertex.position(), 1.0F);
                position.mul(boneStack.last().pose());
                if (Float.isFinite(position.x())
                        && Float.isFinite(position.y())
                        && Float.isFinite(position.z())) {
                    bounds.include(new SkeletonVec3(position.x(), position.y(), position.z()));
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

    static void resetToInitial(GeoBone bone) {
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

    private AzureSourceRigAdapter() {
    }
}
