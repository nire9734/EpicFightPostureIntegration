package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeHeuristics;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Converts Minecraft HierarchicalModel/ModelPart bind rigs into EFPI's common source rig. */
public final class ModelPartSourceRigAdapter {
    private static final float SCALE_EPSILON = 1.0E-5F;
    private static final float GEOMETRY_EPSILON = 1.0E-5F;

    public static Capture capture(HierarchicalModel<?> model, SkeletonKey key) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");

        ModelPart root = Objects.requireNonNull(model.root(), "HierarchicalModel root");
        List<ModelPart> allParts = root.getAllParts().toList();
        if (allParts.isEmpty()) {
            throw new IllegalArgumentException("HierarchicalModel exposes no ModelPart nodes");
        }

        IdentityHashMap<ModelPart, PartState> savedState = new IdentityHashMap<>();
        for (ModelPart part : allParts) {
            savedState.put(part, new PartState(part.storePose(), part.visible, part.skipDraw));
            part.resetPose();
            part.visible = true;
            part.skipDraw = false;
        }

        LinkedHashMap<String, List<String>> paths = new LinkedHashMap<>();
        paths.put("r0", List.of());
        List<CubeEntry> cubes = new ArrayList<>();
        try {
            PoseStack visitorStack = canonicalModelStack();
            root.visit(visitorStack, (pose, path, cubeIndex, cube) -> {
                List<String> segments = parsePath(path);
                registerPrefixes(paths, segments);
                cubes.add(new CubeEntry(
                        nodeId(segments),
                        List.copyOf(segments),
                        cube,
                        new Matrix4f(pose.pose()),
                        new Matrix3f(pose.normal()),
                        SourceNodeHeuristics.pathHasAttachment(segments)
                ));
            });
        } finally {
            for (Map.Entry<ModelPart, PartState> entry : savedState.entrySet()) {
                ModelPart part = entry.getKey();
                PartState state = entry.getValue();
                part.loadPose(state.pose());
                part.visible = state.visible();
                part.skipDraw = state.skipDraw();
            }
        }

        if (cubes.isEmpty()) {
            throw new IllegalArgumentException("HierarchicalModel produced no ModelPart cubes");
        }

        Map<String, List<CubeEntry>> cubesByNode = new LinkedHashMap<>();
        for (CubeEntry cube : cubes) {
            cubesByNode.computeIfAbsent(cube.nodeId(), ignored -> new ArrayList<>()).add(cube);
        }

        List<Map.Entry<String, List<String>>> orderedPaths = paths.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getValue().size()))
                .toList();

        LinkedHashMap<String, SourceNodeSnapshot> nodes = new LinkedHashMap<>();
        LinkedHashMap<String, ModelPart> sourceNodes = new LinkedHashMap<>();
        BoundsAccumulator globalGeometry = new BoundsAccumulator();
        BoundsAccumulator globalPivots = new BoundsAccumulator();
        boolean[] nonUniformScale = {false};

        for (Map.Entry<String, List<String>> entry : orderedPaths) {
            String id = entry.getKey();
            List<String> segments = entry.getValue();
            ModelPart part = resolvePart(root, segments);
            sourceNodes.put(id, part);

            PoseStack bindStack = canonicalModelStack();
            ModelPart cursor = root;
            applyBindTransform(bindStack, cursor, nonUniformScale);
            for (String segment : segments) {
                cursor = cursor.getChild(segment);
                applyBindTransform(bindStack, cursor, nonUniformScale);
            }

            Matrix4f bindPose = new Matrix4f(bindStack.last().pose());
            SkeletonVec3 pivot = transformPoint(bindPose, 0.0F, 0.0F, 0.0F);
            globalPivots.include(pivot);
            Quaternionf q = bindPose.getUnnormalizedRotation(new Quaternionf());
            RigidTransform transform = new RigidTransform(
                    pivot,
                    new SkeletonQuat(q.x(), q.y(), q.z(), q.w())
            );

            boolean attachment = SourceNodeHeuristics.pathHasAttachment(segments);
            BoundsAccumulator own = new BoundsAccumulator();
            for (CubeEntry cube : cubesByNode.getOrDefault(id, List.of())) {
                BodyBounds cubeBounds = captureCubeBounds(cube.pose(), cube.cube());
                if (cubeBounds != null) {
                    own.include(cubeBounds);
                    if (!attachment) {
                        globalGeometry.include(cubeBounds);
                    }
                }
            }

            Optional<BodyBounds> ownBounds = attachment ? Optional.empty() : own.toBounds();
            boolean renderable = ownBounds.map(bounds -> bounds.width() > GEOMETRY_EPSILON
                    || bounds.height() > GEOMETRY_EPSILON
                    || bounds.depth() > GEOMETRY_EPSILON).orElse(false);

            String rawName = segments.isEmpty() ? "root" : segments.get(segments.size() - 1);
            String parentId = segments.isEmpty()
                    ? null
                    : nodeId(segments.subList(0, segments.size() - 1));
            nodes.put(id, new SourceNodeSnapshot(
                    id,
                    semanticName(rawName),
                    Optional.ofNullable(parentId),
                    transform,
                    ownBounds,
                    renderable
            ));
        }

        BodyBounds bounds = globalGeometry.toBounds().or(() -> globalPivots.toBounds())
                .orElseThrow(() -> new IllegalArgumentException("ModelPart rig produced no finite bind-space bounds"));
        return new Capture(
                new SourceRigSnapshot(key, nodes, bounds, nonUniformScale[0]),
                Map.copyOf(sourceNodes),
                List.copyOf(cubes)
        );
    }

    /** Matches Epic Fight's vanilla model conversion basis. */
    static PoseStack canonicalModelStack() {
        PoseStack stack = new PoseStack();
        stack.scale(-1.0F, -1.0F, 1.0F);
        stack.translate(0.0F, -1.5F, 0.0F);
        return stack;
    }

    private static void applyBindTransform(PoseStack stack, ModelPart part, boolean[] nonUniformScale) {
        PartPose pose = part.getInitialPose();
        stack.translate(pose.x / 16.0F, pose.y / 16.0F, pose.z / 16.0F);
        if (pose.xRot != 0.0F || pose.yRot != 0.0F || pose.zRot != 0.0F) {
            stack.mulPose(new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot));
        }
        if (Math.abs(part.xScale - part.yScale) > SCALE_EPSILON
                || Math.abs(part.xScale - part.zScale) > SCALE_EPSILON
                || Math.abs(part.yScale - part.zScale) > SCALE_EPSILON) {
            nonUniformScale[0] = true;
        }
        stack.scale(part.xScale, part.yScale, part.zScale);
    }

    private static ModelPart resolvePart(ModelPart root, List<String> segments) {
        ModelPart cursor = root;
        for (String segment : segments) {
            cursor = cursor.getChild(segment);
        }
        return cursor;
    }

    private static void registerPrefixes(Map<String, List<String>> paths, List<String> segments) {
        for (int i = 1; i <= segments.size(); i++) {
            List<String> prefix = List.copyOf(segments.subList(0, i));
            paths.putIfAbsent(nodeId(prefix), prefix);
        }
    }

    private static List<String> parsePath(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                result.add(segment);
            }
        }
        return List.copyOf(result);
    }

    private static String nodeId(List<String> segments) {
        return segments.isEmpty() ? "r0" : "r0/" + String.join("/", segments);
    }

    /**
     * Common exporter aliases that still mean the second deforming limb segment.
     * Canonicalizing here keeps the framework-neutral semantic analyzer strict.
     */
    private static String semanticName(String rawName) {
        String normalized = rawName == null
                ? ""
                : rawName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return switch (normalized) {
            case "rightfrontarm", "rightforearm", "rightarm2", "rarm2" -> "right_forearm";
            case "leftfrontarm", "leftforearm", "leftarm2", "larm2" -> "left_forearm";
            case "rightfrontleg", "rightforeleg", "rightleg2", "rleg2" -> "right_lower_leg";
            case "leftfrontleg", "leftforeleg", "leftleg2", "lleg2" -> "left_lower_leg";
            default -> rawName == null ? "" : rawName;
        };
    }

    private static BodyBounds captureCubeBounds(Matrix4f pose, ModelPart.Cube cube) {
        if (cube == null) {
            return null;
        }
        BoundsAccumulator bounds = new BoundsAccumulator();
        float[] xs = {cube.minX / 16.0F, cube.maxX / 16.0F};
        float[] ys = {cube.minY / 16.0F, cube.maxY / 16.0F};
        float[] zs = {cube.minZ / 16.0F, cube.maxZ / 16.0F};
        for (float x : xs) {
            for (float y : ys) {
                for (float z : zs) {
                    bounds.include(transformPoint(pose, x, y, z));
                }
            }
        }
        return bounds.toBounds().orElse(null);
    }

    private static SkeletonVec3 transformPoint(Matrix4f pose, float x, float y, float z) {
        Vector4f vector = new Vector4f(x, y, z, 1.0F);
        vector.mul(pose);
        return new SkeletonVec3(vector.x(), vector.y(), vector.z());
    }

    public record Capture(
            SourceRigSnapshot snapshot,
            Map<String, ModelPart> sourceNodes,
            List<CubeEntry> cubes) {
        public Capture {
            Objects.requireNonNull(snapshot, "snapshot");
            sourceNodes = Map.copyOf(sourceNodes);
            cubes = List.copyOf(cubes);
        }
    }

    public record CubeEntry(
            String nodeId,
            List<String> path,
            ModelPart.Cube cube,
            Matrix4f pose,
            Matrix3f normal,
            boolean attachment) {
        public CubeEntry {
            Objects.requireNonNull(nodeId, "nodeId");
            path = List.copyOf(path);
            Objects.requireNonNull(cube, "cube");
            Objects.requireNonNull(pose, "pose");
            Objects.requireNonNull(normal, "normal");
        }
    }

    private record PartState(PartPose pose, boolean visible, boolean skipDraw) {
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

    private ModelPartSourceRigAdapter() {
    }
}
