package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.client.model.tools.BasicModelPart;
import com.github.L_Ender.lionfishapi.client.model.tools.LionfishModelRenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSkeletonResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SemanticJointCandidate;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidModelResolution;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSourceBinding;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Converts Lionfish/Citadel-style {@link AdvancedEntityModel} bind rigs into
 * EFPI's framework-neutral source-rig representation.
 *
 * <p>Lionfish models inherit Minecraft model coordinates: anatomical right is
 * negative X and up is negative Y. LivingEntityRenderer applies the corresponding
 * (-X, -Y, +Z) model-space conversion, so this adapter seeds the same conversion
 * before recording EFPI model-space pivots and geometry.</p>
 */
public final class LionfishSourceRigAdapter {
    private static final float SCALE_EPSILON = 1.0E-5F;
    private static final float GEOMETRY_EPSILON = 1.0E-5F;

    public static Capture capture(AdvancedEntityModel<?> model, SkeletonKey key) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");

        List<AdvancedModelBox> allParts = new ArrayList<>();
        for (AdvancedModelBox part : model.getAllParts()) {
            if (part != null) {
                allParts.add(part);
            }
        }
        if (allParts.isEmpty()) {
            throw new IllegalArgumentException("Lionfish model exposes no AdvancedModelBox parts");
        }

        List<AdvancedModelBox> roots = allParts.stream()
                .filter(part -> part.getParent() == null)
                .toList();
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("Lionfish model exposes no root AdvancedModelBox");
        }

        LinkedHashMap<String, SourceNodeSnapshot> nodes = new LinkedHashMap<>();
        LinkedHashMap<String, AdvancedModelBox> sourceNodes = new LinkedHashMap<>();
        BoundsAccumulator geometryBounds = new BoundsAccumulator();
        BoundsAccumulator pivotBounds = new BoundsAccumulator();
        Set<AdvancedModelBox> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        boolean[] nonUniformScale = {false};

        PoseStack stack = new PoseStack();
        // Minecraft/Lionfish authored model coordinates -> EFPI model space.
        stack.scale(-1.0F, -1.0F, 1.0F);

        for (int rootIndex = 0; rootIndex < roots.size(); rootIndex++) {
            capturePart(
                    roots.get(rootIndex),
                    null,
                    "r" + rootIndex,
                    stack,
                    nodes,
                    sourceNodes,
                    geometryBounds,
                    pivotBounds,
                    nonUniformScale,
                    visited
            );
        }

        BodyBounds bounds = geometryBounds.toBounds()
                .or(() -> pivotBounds.toBounds())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Lionfish model produced no finite bind-space bounds"));

        return new Capture(
                new SourceRigSnapshot(key, nodes, bounds, nonUniformScale[0]),
                Map.copyOf(sourceNodes)
        );
    }

    public static HumanoidModelResolution<AdvancedModelBox> resolve(
            AdvancedEntityModel<?> model,
            SkeletonKey key,
            HumanoidSemanticAnalyzer analyzer) {
        Capture capture = capture(model, key);
        HumanoidSemanticAnalysis analysis = analyzer.analyze(capture.snapshot());
        var descriptor = HumanoidSkeletonResolver.resolve(capture.snapshot(), analysis);

        EnumMap<HumanoidJoint, AdvancedModelBox> semanticSources = new EnumMap<>(HumanoidJoint.class);
        for (Map.Entry<HumanoidJoint, SemanticJointCandidate> entry : analysis.candidates().entrySet()) {
            entry.getValue().sourceNodeId().ifPresent(sourceId -> {
                AdvancedModelBox source = capture.sourceNodes().get(sourceId);
                if (source != null) {
                    semanticSources.put(entry.getKey(), source);
                }
            });
        }

        HumanoidSourceBinding<AdvancedModelBox> binding =
                joint -> Optional.ofNullable(semanticSources.get(joint));
        return new HumanoidModelResolution<>(descriptor, binding);
    }

    private static void capturePart(
            AdvancedModelBox part,
            String parentId,
            String nodeId,
            PoseStack stack,
            Map<String, SourceNodeSnapshot> nodes,
            Map<String, AdvancedModelBox> sourceNodes,
            BoundsAccumulator globalGeometryBounds,
            BoundsAccumulator globalPivotBounds,
            boolean[] nonUniformScale,
            Set<AdvancedModelBox> visited) {
        if (part == null || !visited.add(part)) {
            return;
        }

        BindValues bind = bindValues(part);
        Matrix4f parentPose = new Matrix4f(stack.last().pose());
        SkeletonVec3 pivot = transformPoint(
                parentPose,
                bind.positionX() / 16.0F,
                bind.positionY() / 16.0F,
                bind.positionZ() / 16.0F
        );
        globalPivotBounds.include(pivot);

        if (Math.abs(bind.scaleX() - bind.scaleY()) > SCALE_EPSILON
                || Math.abs(bind.scaleX() - bind.scaleZ()) > SCALE_EPSILON
                || Math.abs(bind.scaleY() - bind.scaleZ()) > SCALE_EPSILON) {
            nonUniformScale[0] = true;
        }

        stack.pushPose();
        stack.translate(
                bind.positionX() / 16.0F,
                bind.positionY() / 16.0F,
                bind.positionZ() / 16.0F
        );
        stack.mulPose(new Quaternionf().rotationZYX(
                bind.rotationZ(),
                bind.rotationY(),
                bind.rotationX()
        ));
        stack.scale(bind.scaleX(), bind.scaleY(), bind.scaleZ());

        Quaternionf sourceRotation = new Matrix4f(stack.last().pose())
                .getUnnormalizedRotation(new Quaternionf());
        RigidTransform bindTransform = new RigidTransform(
                pivot,
                new SkeletonQuat(
                        sourceRotation.x(), sourceRotation.y(),
                        sourceRotation.z(), sourceRotation.w()
                )
        );

        BoundsAccumulator ownGeometry = new BoundsAccumulator();
        if (part.cubeList != null) {
            for (LionfishModelRenderUtils.ModelBox cube : part.cubeList) {
                BodyBounds cubeBounds = captureCubeBounds(stack.last().pose(), cube);
                if (cubeBounds != null) {
                    ownGeometry.include(cubeBounds);
                    globalGeometryBounds.include(cubeBounds);
                }
            }
        }

        Optional<BodyBounds> ownBounds = ownGeometry.toBounds();
        boolean renderable = ownBounds
                .map(value -> value.width() > GEOMETRY_EPSILON
                        || value.height() > GEOMETRY_EPSILON
                        || value.depth() > GEOMETRY_EPSILON)
                .orElse(false);

        nodes.put(nodeId, new SourceNodeSnapshot(
                nodeId,
                part.boxName == null ? "" : part.boxName,
                Optional.ofNullable(parentId),
                bindTransform,
                ownBounds,
                renderable
        ));
        sourceNodes.put(nodeId, part);

        if (part.childModels != null) {
            int childIndex = 0;
            for (BasicModelPart child : part.childModels) {
                if (child instanceof AdvancedModelBox advancedChild) {
                    capturePart(
                            advancedChild,
                            nodeId,
                            nodeId + "/" + childIndex,
                            stack,
                            nodes,
                            sourceNodes,
                            globalGeometryBounds,
                            globalPivotBounds,
                            nonUniformScale,
                            visited
                    );
                    childIndex++;
                }
            }
        }

        stack.popPose();
    }

    private static BindValues bindValues(AdvancedModelBox part) {
        boolean hasStoredDefault = part.defaultscaleX != 0.0F
                || part.defaultscaleY != 0.0F
                || part.defaultscaleZ != 0.0F;
        if (hasStoredDefault) {
            return new BindValues(
                    part.defaultPositionX, part.defaultPositionY, part.defaultPositionZ,
                    part.defaultRotationX, part.defaultRotationY, part.defaultRotationZ,
                    part.defaultscaleX, part.defaultscaleY, part.defaultscaleZ
            );
        }
        return new BindValues(
                part.rotationPointX, part.rotationPointY, part.rotationPointZ,
                part.rotateAngleX, part.rotateAngleY, part.rotateAngleZ,
                part.xScale, part.yScale, part.zScale
        );
    }

    private static BodyBounds captureCubeBounds(Matrix4f pose, LionfishModelRenderUtils.ModelBox cube) {
        if (cube == null) {
            return null;
        }
        BoundsAccumulator bounds = new BoundsAccumulator();
        float[] xs = {cube.posX1 / 16.0F, cube.posX2 / 16.0F};
        float[] ys = {cube.posY1 / 16.0F, cube.posY2 / 16.0F};
        float[] zs = {cube.posZ1 / 16.0F, cube.posZ2 / 16.0F};
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
        Vector4f transformed = new Vector4f(x, y, z, 1.0F);
        transformed.mul(pose);
        return new SkeletonVec3(transformed.x(), transformed.y(), transformed.z());
    }

    public record Capture(
            SourceRigSnapshot snapshot,
            Map<String, AdvancedModelBox> sourceNodes) {
        public Capture {
            Objects.requireNonNull(snapshot, "snapshot");
            sourceNodes = sourceNodes == null ? Map.of() : Map.copyOf(sourceNodes);
        }
    }

    private record BindValues(
            float positionX, float positionY, float positionZ,
            float rotationX, float rotationY, float rotationZ,
            float scaleX, float scaleY, float scaleZ) {
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

    private LionfishSourceRigAdapter() {
    }
}
