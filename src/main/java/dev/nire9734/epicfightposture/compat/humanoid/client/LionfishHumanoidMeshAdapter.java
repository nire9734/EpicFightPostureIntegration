package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.client.model.tools.BasicModelPart;
import com.github.L_Ender.lionfishapi.client.model.tools.LionfishModelRenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
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
import java.util.function.Supplier;

/** One-time Lionfish/Citadel-style humanoid -> Epic Fight SkinnedMesh baker. */
public final class LionfishHumanoidMeshAdapter {
    private static final MeshPartDefinition PART = new RuntimeLionfishPart("lionfish_humanoid");
    private static volatile ReflectionAccess reflectionAccess;

    public static Result analyseAndBake(AdvancedEntityModel<?> model, SkeletonKey key) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");
        try {
            LionfishSourceRigAdapter.Capture capture = LionfishSourceRigAdapter.capture(model, key);
            HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(capture.snapshot());
            HumanoidSkeletonDescriptor descriptor = HumanoidSkeletonResolver.resolve(capture.snapshot(), analysis);
            HumanoidSkinWeightResolver weights = new HumanoidSkinWeightResolver(capture.snapshot(), analysis, descriptor);
            SkinnedMesh mesh = bake(model, capture, weights);
            return mesh == null
                    ? Result.unsupported("mesh bake produced no vertices")
                    : Result.supported(descriptor, mesh);
        } catch (Throwable throwable) {
            return Result.unsupported("Lionfish humanoid mesh bake failed: "
                    + throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : " (" + throwable.getMessage() + ")"));
        }
    }

    private static SkinnedMesh bake(
            AdvancedEntityModel<?> model,
            LionfishSourceRigAdapter.Capture capture,
            HumanoidSkinWeightResolver weights) throws ReflectiveOperationException {
        IdentityHashMap<AdvancedModelBox, String> nodeIds = new IdentityHashMap<>();
        capture.sourceNodes().forEach((id, node) -> nodeIds.put(node, id));

        List<SingleGroupVertexBuilder> vertices = new ArrayList<>();
        Map<MeshPartDefinition, IntList> indices = new LinkedHashMap<>();
        PoseStack stack = new PoseStack();
        stack.scale(-1.0F, -1.0F, 1.0F);

        for (AdvancedModelBox part : model.getAllParts()) {
            if (part != null && part.getParent() == null) {
                bakePart(part, stack, nodeIds, weights, vertices, indices);
            }
        }
        return vertices.isEmpty() ? null : SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
    }

    private static void bakePart(
            AdvancedModelBox part,
            PoseStack stack,
            IdentityHashMap<AdvancedModelBox, String> nodeIds,
            HumanoidSkinWeightResolver weights,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) throws ReflectiveOperationException {
        if (part == null || !part.showModel) return;
        String sourceNodeId = nodeIds.get(part);
        if (sourceNodeId == null) return;

        BindValues bind = bindValues(part);
        stack.pushPose();
        stack.translate(bind.positionX / 16.0F, bind.positionY / 16.0F, bind.positionZ / 16.0F);
        stack.mulPose(new Quaternionf().rotationZYX(bind.rotationZ, bind.rotationY, bind.rotationX));
        stack.scale(bind.scaleX, bind.scaleY, bind.scaleZ);

        if (!part.isHidden() && part.cubeList != null) {
            for (LionfishModelRenderUtils.ModelBox cube : part.cubeList) {
                bakeCube(cube, sourceNodeId, stack, weights, vertices, indices);
            }
        }
        if (part.childModels != null) {
            for (BasicModelPart child : part.childModels) {
                if (child instanceof AdvancedModelBox advancedChild) {
                    bakePart(advancedChild, stack, nodeIds, weights, vertices, indices);
                }
            }
        }
        stack.popPose();
    }

    private static void bakeCube(
            LionfishModelRenderUtils.ModelBox cube,
            String sourceNodeId,
            PoseStack stack,
            HumanoidSkinWeightResolver weights,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) throws ReflectiveOperationException {
        if (cube == null) return;
        ReflectionAccess access = reflection();
        Object quads = access.quadsField.get(cube);
        if (quads == null) return;

        for (int q = 0; q < Array.getLength(quads); q++) {
            Object quad = Array.get(quads, q);
            if (quad == null) continue;
            QuadFields qf = access.quadFields(quad.getClass());
            Object sourceVertices = qf.vertices.get(quad);
            Object sourceNormalValue = qf.normal.get(quad);
            if (sourceVertices == null || Array.getLength(sourceVertices) != 4
                    || !(sourceNormalValue instanceof Vector3f sourceNormal)) continue;

            Vector3f normal = new Vector3f(sourceNormal);
            normal.mul(stack.last().normal());
            if (normal.lengthSquared() > 1.0E-8F) normal.normalize();

            int first = vertices.size();
            List<SingleGroupVertexBuilder> built = new ArrayList<>(4);
            boolean complete = true;
            for (int v = 0; v < 4; v++) {
                Object sourceVertex = Array.get(sourceVertices, v);
                if (sourceVertex == null) { complete = false; break; }
                VertexFields vf = access.vertexFields(sourceVertex.getClass());
                Object sourcePositionValue = vf.position.get(sourceVertex);
                if (!(sourcePositionValue instanceof Vector3f sourcePosition)) { complete = false; break; }

                Vector4f transformed = new Vector4f(
                        sourcePosition.x() / 16.0F,
                        sourcePosition.y() / 16.0F,
                        sourcePosition.z() / 16.0F,
                        1.0F);
                transformed.mul(stack.last().pose());
                SkeletonVec3 position = new SkeletonVec3(transformed.x(), transformed.y(), transformed.z());
                HumanoidSkinWeightResolver.SkinWeights skin = weights.weights(sourceNodeId, position);

                built.add(new SingleGroupVertexBuilder()
                        .setPosition(new Vec3f(position.x(), position.y(), position.z()))
                        .setNormal(new Vec3f(normal.x(), normal.y(), normal.z()))
                        .setTextureCoordinate(new Vec2f(vf.textureU.getFloat(sourceVertex), vf.textureV.getFloat(sourceVertex)))
                        .setEffectiveJointIDs(new Vec3f(skin.firstJoint(), skin.secondJoint(), 0.0F))
                        .setEffectiveJointWeights(new Vec3f(skin.firstWeight(), skin.secondWeight(), 0.0F))
                        .setEffectiveJointNumber(skin.count()));
            }
            if (!complete || built.size() != 4) continue;
            vertices.addAll(built);
            triangulateQuad(indices, first);
        }
    }

    private static void triangulateQuad(Map<MeshPartDefinition, IntList> indices, int first) {
        IntList list = indices.computeIfAbsent(PART, ignored -> new IntArrayList());
        addIndexTriplet(list, first);
        addIndexTriplet(list, first + 1);
        addIndexTriplet(list, first + 3);
        addIndexTriplet(list, first + 3);
        addIndexTriplet(list, first + 1);
        addIndexTriplet(list, first + 2);
    }

    private static void addIndexTriplet(IntList list, int index) {
        list.add(index); list.add(index); list.add(index);
    }

    private static BindValues bindValues(AdvancedModelBox part) {
        boolean stored = part.defaultscaleX != 0.0F || part.defaultscaleY != 0.0F || part.defaultscaleZ != 0.0F;
        return stored
                ? new BindValues(part.defaultPositionX, part.defaultPositionY, part.defaultPositionZ,
                        part.defaultRotationX, part.defaultRotationY, part.defaultRotationZ,
                        part.defaultscaleX, part.defaultscaleY, part.defaultscaleZ)
                : new BindValues(part.rotationPointX, part.rotationPointY, part.rotationPointZ,
                        part.rotateAngleX, part.rotateAngleY, part.rotateAngleZ,
                        part.xScale, part.yScale, part.zScale);
    }

    private static ReflectionAccess reflection() throws ReflectiveOperationException {
        ReflectionAccess cached = reflectionAccess;
        if (cached != null) return cached;
        synchronized (LionfishHumanoidMeshAdapter.class) {
            if (reflectionAccess == null) {
                Field quads = LionfishModelRenderUtils.ModelBox.class.getDeclaredField("quads");
                quads.setAccessible(true);
                reflectionAccess = new ReflectionAccess(quads);
            }
            return reflectionAccess;
        }
    }

    public record Result(boolean supported, HumanoidSkeletonDescriptor descriptor, SkinnedMesh mesh, String reason) {
        public static Result supported(HumanoidSkeletonDescriptor descriptor, SkinnedMesh mesh) {
            return new Result(true, descriptor, mesh, "ok");
        }
        public static Result unsupported(String reason) {
            return new Result(false, null, null, reason);
        }
    }

    private record BindValues(
            float positionX, float positionY, float positionZ,
            float rotationX, float rotationY, float rotationZ,
            float scaleX, float scaleY, float scaleZ) {}

    private static final class ReflectionAccess {
        private final Field quadsField;
        private final Map<Class<?>, QuadFields> quads = new HashMap<>();
        private final Map<Class<?>, VertexFields> vertices = new HashMap<>();
        private ReflectionAccess(Field quadsField) { this.quadsField = quadsField; }

        synchronized QuadFields quadFields(Class<?> type) throws ReflectiveOperationException {
            QuadFields fields = quads.get(type);
            if (fields == null) {
                Field vertexPositions = type.getDeclaredField("vertexPositions");
                Field normal = type.getDeclaredField("normal");
                vertexPositions.setAccessible(true); normal.setAccessible(true);
                fields = new QuadFields(vertexPositions, normal);
                quads.put(type, fields);
            }
            return fields;
        }

        synchronized VertexFields vertexFields(Class<?> type) throws ReflectiveOperationException {
            VertexFields fields = vertices.get(type);
            if (fields == null) {
                Field position = type.getDeclaredField("position");
                Field textureU = type.getDeclaredField("textureU");
                Field textureV = type.getDeclaredField("textureV");
                position.setAccessible(true); textureU.setAccessible(true); textureV.setAccessible(true);
                fields = new VertexFields(position, textureU, textureV);
                vertices.put(type, fields);
            }
            return fields;
        }
    }

    private record QuadFields(Field vertices, Field normal) {}
    private record VertexFields(Field position, Field textureU, Field textureV) {}

    private record RuntimeLionfishPart(String partName) implements MeshPartDefinition {
        @Override public Mesh.RenderProperties renderProperties() { return null; }
        @Override public Supplier<OpenMatrix4f> getModelPartAnimationProvider() { return () -> null; }
    }

    private LionfishHumanoidMeshAdapter() {}
}
