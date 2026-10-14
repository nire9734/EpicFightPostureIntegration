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
            return mesh == null ? Result.unsupported("mesh bake produced no vertices") : Result.supported(descriptor, mesh);
        } catch (Throwable throwable) {
            return Result.unsupported("Lionfish humanoid mesh bake failed: " + throwable.getClass().getSimpleName()
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
        PoseStack stack = LionfishSourceRigAdapter.canonicalModelStack();

        for (AdvancedModelBox part : model.getAllParts()) {
            if (part != null && part.getParent() == null) bakePart(part, stack, nodeIds, weights, vertices, indices);
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

        LionfishSourceRigAdapter.BindValues bind = LionfishSourceRigAdapter.bindValues(part);
        stack.pushPose();
        stack.translate(bind.positionX() / 16.0F, bind.positionY() / 16.0F, bind.positionZ() / 16.0F);
        stack.mulPose(new Quaternionf().rotationZYX(bind.rotationZ(), bind.rotationY(), bind.rotationX()));
        stack.scale(bind.scaleX(), bind.scaleY(), bind.scaleZ());

        if (!part.isHidden() && part.cubeList != null) {
            for (LionfishModelRenderUtils.ModelBox cube : part.cubeList) {
                bakeCube(cube, sourceNodeId, stack, weights, vertices, indices);
            }
        }
        if (part.childModels != null) {
            for (BasicModelPart child : part.childModels) {
                if (child instanceof AdvancedModelBox box) bakePart(box, stack, nodeIds, weights, vertices, indices);
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
            Object normalValue = qf.normal.get(quad);
            if (sourceVertices == null || Array.getLength(sourceVertices) != 4
                    || !(normalValue instanceof Vector3f sourceNormal)) continue;

            Vector3f normal = new Vector3f(sourceNormal);
            normal.mul(stack.last().normal());
            if (normal.lengthSquared() > 1.0E-8F) normal.normalize();

            int first = vertices.size();
            List<SingleGroupVertexBuilder> built = new ArrayList<>(4);
            for (int v = 0; v < 4; v++) {
                Object sourceVertex = Array.get(sourceVertices, v);
                if (sourceVertex == null) { built.clear(); break; }
                VertexFields vf = access.vertexFields(sourceVertex.getClass());
                Object positionValue = vf.position.get(sourceVertex);
                if (!(positionValue instanceof Vector3f sourcePosition)) { built.clear(); break; }

                Vector4f p = new Vector4f(sourcePosition.x() / 16.0F, sourcePosition.y() / 16.0F,
                        sourcePosition.z() / 16.0F, 1.0F);
                p.mul(stack.last().pose());
                SkeletonVec3 position = new SkeletonVec3(p.x(), p.y(), p.z());
                HumanoidSkinWeightResolver.SkinWeights skin = weights.weights(sourceNodeId, position);
                built.add(new SingleGroupVertexBuilder()
                        .setPosition(new Vec3f(position.x(), position.y(), position.z()))
                        .setNormal(new Vec3f(normal.x(), normal.y(), normal.z()))
                        .setTextureCoordinate(new Vec2f(vf.textureU.getFloat(sourceVertex), vf.textureV.getFloat(sourceVertex)))
                        .setEffectiveJointIDs(new Vec3f(skin.firstJoint(), skin.secondJoint(), 0.0F))
                        .setEffectiveJointWeights(new Vec3f(skin.firstWeight(), skin.secondWeight(), 0.0F))
                        .setEffectiveJointNumber(skin.count()));
            }
            if (built.size() != 4) continue;
            vertices.addAll(built);
            triangulateQuad(indices, first);
        }
    }

    private static void triangulateQuad(Map<MeshPartDefinition, IntList> indices, int first) {
        IntList list = indices.computeIfAbsent(PART, ignored -> new IntArrayList());
        add3(list, first); add3(list, first + 1); add3(list, first + 3);
        add3(list, first + 3); add3(list, first + 1); add3(list, first + 2);
    }

    private static void add3(IntList list, int value) { list.add(value); list.add(value); list.add(value); }

    private static ReflectionAccess reflection() throws ReflectiveOperationException {
        if (reflectionAccess != null) return reflectionAccess;
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
        static Result unsupported(String reason) { return new Result(false, null, null, reason); }
    }

    private static final class ReflectionAccess {
        final Field quadsField;
        final Map<Class<?>, QuadFields> quads = new HashMap<>();
        final Map<Class<?>, VertexFields> vertices = new HashMap<>();
        ReflectionAccess(Field field) { quadsField = field; }
        synchronized QuadFields quadFields(Class<?> type) throws ReflectiveOperationException {
            QuadFields f = quads.get(type);
            if (f == null) {
                Field vs = type.getDeclaredField("vertexPositions"), n = type.getDeclaredField("normal");
                vs.setAccessible(true); n.setAccessible(true); f = new QuadFields(vs, n); quads.put(type, f);
            }
            return f;
        }
        synchronized VertexFields vertexFields(Class<?> type) throws ReflectiveOperationException {
            VertexFields f = vertices.get(type);
            if (f == null) {
                Field p = type.getDeclaredField("position"), u = type.getDeclaredField("textureU"), v = type.getDeclaredField("textureV");
                p.setAccessible(true); u.setAccessible(true); v.setAccessible(true); f = new VertexFields(p, u, v); vertices.put(type, f);
            }
            return f;
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
