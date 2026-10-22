package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.client.model.tools.BasicModelPart;
import com.github.L_Ender.lionfishapi.client.model.tools.LionfishModelRenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.SecondaryDeformationPolicy;
import dev.nire9734.epicfightposture.compat.humanoid.rig.ConservativeHumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSkeletonResolver;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.MeshPartDefinition;
import yesman.epicfight.api.client.model.SingleGroupVertexBuilder;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec2f;
import yesman.epicfight.api.utils.math.Vec3f;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** One-time Lionfish/Citadel-style humanoid -> Epic Fight SkinnedMesh baker. */
public final class LionfishHumanoidMeshAdapter {
    private static final MeshPartDefinition PART = new RuntimeLionfishPart("lionfish_humanoid");
    private static final float PLANAR_EPSILON_PIXELS = 1.0E-4F;
    private static volatile ReflectionAccess reflectionAccess;

    public static Result analyseAndBake(AdvancedEntityModel<?> model, SkeletonKey key) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");
        try {
            LionfishSourceRigAdapter.Capture capture = LionfishSourceRigAdapter.capture(model, key);
            HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(capture.snapshot());
            HumanoidSkeletonDescriptor descriptor = HumanoidSkeletonResolver.resolve(capture.snapshot(), analysis);
            HumanoidSkinWeightResolver weights = new HumanoidSkinWeightResolver(capture.snapshot(), analysis, descriptor);
            BakeOutput baked = bake(model, capture, weights, false);
            return baked == null || baked.mesh() == null
                    ? Result.unsupported("mesh bake produced no vertices")
                    : Result.supported(descriptor, baked.mesh());
        } catch (Throwable throwable) {
            return Result.unsupported("Lionfish humanoid mesh bake failed: " + throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : " (" + throwable.getMessage() + ")"));
        }
    }

    /**
     * Bakes a layer-owned Lionfish model against the primary body's canonical
     * descriptor. The secondary hierarchy supplies semantic ownership only; it
     * never creates an independent elbow/knee/spine layout.
     *
     * <p>Rigid-only models intentionally return unsupported so their original
     * LayerPoseOverrideContext path remains in charge. Only models containing at
     * least one safely classified deformable primitive are substituted.</p>
     */
    static SecondaryResult analyseAndBakeSecondary(
            AdvancedEntityModel<?> model,
            SkeletonKey key,
            HumanoidSkeletonDescriptor primaryDescriptor) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(primaryDescriptor, "primaryDescriptor");
        try {
            LionfishSourceRigAdapter.Capture capture = LionfishSourceRigAdapter.capture(model, key);
            HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(capture.snapshot());
            HumanoidSkinWeightResolver weights =
                    new HumanoidSkinWeightResolver(capture.snapshot(), analysis, primaryDescriptor);
            BakeOutput baked = bake(model, capture, weights, true);
            if (baked == null || baked.mesh() == null) {
                return SecondaryResult.unsupported("secondary mesh bake produced no vertices");
            }
            if (!baked.deformableGeometry()) {
                return SecondaryResult.unsupported("secondary geometry is rigid-only");
            }
            return SecondaryResult.supported(baked.mesh());
        } catch (Throwable throwable) {
            return SecondaryResult.unsupported(
                    "Lionfish secondary mesh bake failed: " + throwable.getClass().getSimpleName()
                            + (throwable.getMessage() == null ? "" : " (" + throwable.getMessage() + ")")
            );
        }
    }

    private static BakeOutput bake(
            AdvancedEntityModel<?> model,
            LionfishSourceRigAdapter.Capture capture,
            HumanoidSkinWeightResolver weights,
            boolean failOnAmbiguousSecondary) throws ReflectiveOperationException {
        IdentityHashMap<AdvancedModelBox, String> nodeIds = new IdentityHashMap<>();
        capture.sourceNodes().forEach((id, node) -> nodeIds.put(node, id));
        List<SingleGroupVertexBuilder> vertices = new ArrayList<>();
        Map<MeshPartDefinition, IntList> indices = new LinkedHashMap<>();
        PoseStack stack = LionfishSourceRigAdapter.canonicalModelStack();
        BakeStats stats = new BakeStats();

        for (AdvancedModelBox part : model.getAllParts()) {
            if (part != null && part.getParent() == null) {
                if (!bakePart(
                        part,
                        stack,
                        nodeIds,
                        weights,
                        failOnAmbiguousSecondary,
                        stats,
                        vertices,
                        indices)) {
                    return null;
                }
            }
        }
        if (vertices.isEmpty()) {
            return null;
        }
        return new BakeOutput(
                SingleGroupVertexBuilder.loadVertexInformation(vertices, indices),
                stats.deformableGeometry
        );
    }

    private static boolean bakePart(
            AdvancedModelBox part,
            PoseStack stack,
            IdentityHashMap<AdvancedModelBox, String> nodeIds,
            HumanoidSkinWeightResolver weights,
            boolean failOnAmbiguousSecondary,
            BakeStats stats,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) throws ReflectiveOperationException {
        if (part == null || !part.showModel) {
            return true;
        }
        String sourceNodeId = nodeIds.get(part);
        if (sourceNodeId == null) {
            return !failOnAmbiguousSecondary;
        }

        LionfishSourceRigAdapter.BindValues bind = LionfishSourceRigAdapter.bindValues(part);
        stack.pushPose();
        try {
            stack.translate(bind.positionX() / 16.0F, bind.positionY() / 16.0F, bind.positionZ() / 16.0F);
            stack.mulPose(new Quaternionf().rotationZYX(bind.rotationZ(), bind.rotationY(), bind.rotationX()));
            stack.scale(bind.scaleX(), bind.scaleY(), bind.scaleZ());

            if (!part.isHidden() && part.cubeList != null) {
                for (LionfishModelRenderUtils.ModelBox cube : part.cubeList) {
                    if (!bakeCube(
                            cube,
                            sourceNodeId,
                            stack,
                            weights,
                            failOnAmbiguousSecondary,
                            stats,
                            vertices,
                            indices)) {
                        return false;
                    }
                }
            }
            if (part.childModels != null) {
                for (BasicModelPart child : part.childModels) {
                    if (child instanceof AdvancedModelBox box
                            && !bakePart(
                            box,
                            stack,
                            nodeIds,
                            weights,
                            failOnAmbiguousSecondary,
                            stats,
                            vertices,
                            indices)) {
                        return false;
                    }
                }
            }
            return true;
        } finally {
            stack.popPose();
        }
    }

    private static boolean bakeCube(
            LionfishModelRenderUtils.ModelBox cube,
            String sourceNodeId,
            PoseStack stack,
            HumanoidSkinWeightResolver weights,
            boolean failOnAmbiguousSecondary,
            BakeStats stats,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) throws ReflectiveOperationException {
        if (cube == null) {
            return true;
        }
        ReflectionAccess access = reflection();
        Object quads = access.quadsField.get(cube);
        if (quads == null) {
            return true;
        }
        boolean surfaceAttachment = isPlanarCube(cube);

        for (int q = 0; q < Array.getLength(quads); q++) {
            Object quad = Array.get(quads, q);
            if (quad == null) {
                continue;
            }
            QuadFields qf = access.quadFields(quad.getClass());
            Object sourceVertices = qf.vertices.get(quad);
            Object normalValue = qf.normal.get(quad);
            if (sourceVertices == null || Array.getLength(sourceVertices) != 4
                    || !(normalValue instanceof Vector3f sourceNormal)) {
                continue;
            }

            SourceVertex[] corners = readQuadVertices(sourceVertices, access);
            if (corners == null) {
                continue;
            }

            Vector3f normal = new Vector3f(sourceNormal);
            normal.mul(stack.last().normal());
            if (normal.lengthSquared() > 1.0E-8F) {
                normal.normalize();
            }

            HumanoidSkinWeightResolver.SkinWeights[] cornerWeights =
                    new HumanoidSkinWeightResolver.SkinWeights[4];
            for (int i = 0; i < corners.length; i++) {
                SkeletonVec3 position = modelPosition(corners[i].position(), stack);
                cornerWeights[i] = weights.weights(sourceNodeId, position);
            }

            Optional<SecondaryDeformationPolicy.Decision> decision = surfaceAttachment
                    ? SecondaryDeformationPolicy.classifySurfaceAttachment(List.of(cornerWeights))
                    : SecondaryDeformationPolicy.classify(List.of(cornerWeights));
            if (decision.isEmpty() && failOnAmbiguousSecondary) {
                return false;
            }

            int rigidJoint = -1;
            boolean segmentedAttachment = false;
            if (decision.isPresent()) {
                if (decision.get().rigid()) {
                    rigidJoint = decision.get().rigidJointId();
                } else {
                    segmentedAttachment = decision.get().segmented();
                    stats.deformableGeometry = true;
                }
            }

            boolean uVaries = rigidJoint < 0 && (
                    SkinningSubdivision.weightsDiffer(cornerWeights[0], cornerWeights[1])
                            || SkinningSubdivision.weightsDiffer(cornerWeights[3], cornerWeights[2]));
            boolean vVaries = rigidJoint < 0 && (
                    SkinningSubdivision.weightsDiffer(cornerWeights[0], cornerWeights[3])
                            || SkinningSubdivision.weightsDiffer(cornerWeights[1], cornerWeights[2]));

            float uLengthPixels = Math.max(
                    corners[0].position().distance(corners[1].position()),
                    corners[3].position().distance(corners[2].position())
            );
            float vLengthPixels = Math.max(
                    corners[0].position().distance(corners[3].position()),
                    corners[1].position().distance(corners[2].position())
            );
            int uSegments = SkinningSubdivision.segmentCount(uLengthPixels, uVaries);
            int vSegments = SkinningSubdivision.segmentCount(vLengthPixels, vVaries);

            bakeSubdividedQuad(
                    corners,
                    normal,
                    sourceNodeId,
                    stack,
                    weights,
                    rigidJoint,
                    segmentedAttachment,
                    uSegments,
                    vSegments,
                    vertices,
                    indices
            );
        }
        return true;
    }

    private static boolean isPlanarCube(LionfishModelRenderUtils.ModelBox cube) {
        int collapsedAxes = 0;
        if (Math.abs(cube.posX2 - cube.posX1) <= PLANAR_EPSILON_PIXELS) collapsedAxes++;
        if (Math.abs(cube.posY2 - cube.posY1) <= PLANAR_EPSILON_PIXELS) collapsedAxes++;
        if (Math.abs(cube.posZ2 - cube.posZ1) <= PLANAR_EPSILON_PIXELS) collapsedAxes++;
        return collapsedAxes >= 1;
    }

    private static SourceVertex[] readQuadVertices(
            Object sourceVertices,
            ReflectionAccess access) throws ReflectiveOperationException {
        SourceVertex[] result = new SourceVertex[4];
        for (int i = 0; i < result.length; i++) {
            Object sourceVertex = Array.get(sourceVertices, i);
            if (sourceVertex == null) {
                return null;
            }
            VertexFields vf = access.vertexFields(sourceVertex.getClass());
            Object positionValue = vf.position.get(sourceVertex);
            if (!(positionValue instanceof Vector3f sourcePosition)) {
                return null;
            }
            result[i] = new SourceVertex(
                    new Vector3f(sourcePosition),
                    vf.textureU.getFloat(sourceVertex),
                    vf.textureV.getFloat(sourceVertex)
            );
        }
        return result;
    }

    private static void bakeSubdividedQuad(
            SourceVertex[] corners,
            Vector3f normal,
            String sourceNodeId,
            PoseStack stack,
            HumanoidSkinWeightResolver weights,
            int rigidJoint,
            boolean segmentedAttachment,
            int uSegments,
            int vSegments,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) {
        int first = vertices.size();
        for (int vIndex = 0; vIndex <= vSegments; vIndex++) {
            float v = (float) vIndex / (float) vSegments;
            for (int uIndex = 0; uIndex <= uSegments; uIndex++) {
                float u = (float) uIndex / (float) uSegments;
                SourceVertex sample = interpolate(corners, u, v);
                SkeletonVec3 position = modelPosition(sample.position(), stack);
                HumanoidSkinWeightResolver.SkinWeights sampled = weights.weights(sourceNodeId, position);
                HumanoidSkinWeightResolver.SkinWeights skin;
                if (rigidJoint >= 0) {
                    skin = HumanoidSkinWeightResolver.SkinWeights.rigid(rigidJoint);
                } else if (segmentedAttachment) {
                    skin = HumanoidSkinWeightResolver.SkinWeights.rigid(
                            SecondaryDeformationPolicy.dominantJoint(sampled));
                } else {
                    skin = sampled;
                }
                vertices.add(new SingleGroupVertexBuilder()
                        .setPosition(new Vec3f(position.x(), position.y(), position.z()))
                        .setNormal(new Vec3f(normal.x(), normal.y(), normal.z()))
                        .setTextureCoordinate(new Vec2f(sample.textureU(), sample.textureV()))
                        .setEffectiveJointIDs(new Vec3f(skin.firstJoint(), skin.secondJoint(), 0.0F))
                        .setEffectiveJointWeights(new Vec3f(skin.firstWeight(), skin.secondWeight(), 0.0F))
                        .setEffectiveJointNumber(skin.count()));
            }
        }

        IntList list = indices.computeIfAbsent(PART, ignored -> new IntArrayList());
        int rowWidth = uSegments + 1;
        for (int vIndex = 0; vIndex < vSegments; vIndex++) {
            for (int uIndex = 0; uIndex < uSegments; uIndex++) {
                int topLeft = first + vIndex * rowWidth + uIndex;
                int topRight = topLeft + 1;
                int bottomLeft = topLeft + rowWidth;
                int bottomRight = bottomLeft + 1;
                addTriangle(list, topLeft, topRight, bottomLeft);
                addTriangle(list, bottomLeft, topRight, bottomRight);
            }
        }
    }

    private static SourceVertex interpolate(SourceVertex[] corners, float u, float v) {
        Vector3f top = new Vector3f(corners[0].position()).lerp(corners[1].position(), u);
        Vector3f bottom = new Vector3f(corners[3].position()).lerp(corners[2].position(), u);
        Vector3f position = top.lerp(bottom, v);

        float topU = lerp(corners[0].textureU(), corners[1].textureU(), u);
        float bottomU = lerp(corners[3].textureU(), corners[2].textureU(), u);
        float textureU = lerp(topU, bottomU, v);
        float topV = lerp(corners[0].textureV(), corners[1].textureV(), u);
        float bottomV = lerp(corners[3].textureV(), corners[2].textureV(), u);
        float textureV = lerp(topV, bottomV, v);
        return new SourceVertex(position, textureU, textureV);
    }

    private static SkeletonVec3 modelPosition(Vector3f sourcePosition, PoseStack stack) {
        Vector4f p = new Vector4f(
                sourcePosition.x() / 16.0F,
                sourcePosition.y() / 16.0F,
                sourcePosition.z() / 16.0F,
                1.0F
        );
        p.mul(stack.last().pose());
        return new SkeletonVec3(p.x(), p.y(), p.z());
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static void addTriangle(IntList list, int first, int second, int third) {
        add3(list, first);
        add3(list, second);
        add3(list, third);
    }

    private static void add3(IntList list, int value) {
        list.add(value);
        list.add(value);
        list.add(value);
    }

    private static ReflectionAccess reflection() throws ReflectiveOperationException {
        if (reflectionAccess != null) {
            return reflectionAccess;
        }
        synchronized (LionfishHumanoidMeshAdapter.class) {
            if (reflectionAccess == null) {
                Field field = LionfishModelRenderUtils.ModelBox.class.getDeclaredField("quads");
                field.setAccessible(true);
                reflectionAccess = new ReflectionAccess(field);
            }
            return reflectionAccess;
        }
    }

    public record Result(boolean supported, HumanoidSkeletonDescriptor descriptor, SkinnedMesh mesh, String reason) {
        static Result supported(HumanoidSkeletonDescriptor descriptor, SkinnedMesh mesh) {
            return new Result(true, descriptor, mesh, "ok");
        }

        static Result unsupported(String reason) {
            return new Result(false, null, null, reason);
        }
    }

    record SecondaryResult(boolean supported, SkinnedMesh mesh, String reason) {
        static SecondaryResult supported(SkinnedMesh mesh) {
            return new SecondaryResult(true, mesh, "ok");
        }

        static SecondaryResult unsupported(String reason) {
            return new SecondaryResult(false, null, reason);
        }
    }

    private record BakeOutput(SkinnedMesh mesh, boolean deformableGeometry) {
    }

    private static final class BakeStats {
        boolean deformableGeometry;
    }

    private static final class ReflectionAccess {
        final Field quadsField;
        final Map<Class<?>, QuadFields> quads = new HashMap<>();
        final Map<Class<?>, VertexFields> vertices = new HashMap<>();

        ReflectionAccess(Field field) {
            quadsField = field;
        }

        synchronized QuadFields quadFields(Class<?> type) throws ReflectiveOperationException {
            QuadFields f = quads.get(type);
            if (f == null) {
                Field vs = type.getDeclaredField("vertexPositions"), n = type.getDeclaredField("normal");
                vs.setAccessible(true);
                n.setAccessible(true);
                f = new QuadFields(vs, n);
                quads.put(type, f);
            }
            return f;
        }

        synchronized VertexFields vertexFields(Class<?> type) throws ReflectiveOperationException {
            VertexFields f = vertices.get(type);
            if (f == null) {
                Field p = type.getDeclaredField("position"),
                        u = type.getDeclaredField("textureU"),
                        v = type.getDeclaredField("textureV");
                p.setAccessible(true);
                u.setAccessible(true);
                v.setAccessible(true);
                f = new VertexFields(p, u, v);
                vertices.put(type, f);
            }
            return f;
        }
    }

    private record SourceVertex(Vector3f position, float textureU, float textureV) {
    }

    private record QuadFields(Field vertices, Field normal) {
    }

    private record VertexFields(Field position, Field textureU, Field textureV) {
    }

    private record RuntimeLionfishPart(String partName) implements MeshPartDefinition {
        @Override
        public Mesh.RenderProperties renderProperties() {
            return null;
        }

        @Override
        public Supplier<OpenMatrix4f> getModelPartAnimationProvider() {
            return () -> null;
        }
    }

    private LionfishHumanoidMeshAdapter() {
    }
}
