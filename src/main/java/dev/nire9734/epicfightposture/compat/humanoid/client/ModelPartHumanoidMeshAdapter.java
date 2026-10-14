package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.ConservativeHumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSkeletonResolver;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** One-time Minecraft HierarchicalModel/ModelPart -> Epic Fight SkinnedMesh baker. */
public final class ModelPartHumanoidMeshAdapter {
    private static final MeshPartDefinition PART = new RuntimeModelPart("modelpart_humanoid");
    private static volatile Field cubePolygonsField;
    private static final Map<Class<?>, PolygonFields> POLYGON_FIELDS = new HashMap<>();
    private static final Map<Class<?>, VertexFields> VERTEX_FIELDS = new HashMap<>();

    public static Result analyseAndBake(HierarchicalModel<?> model, SkeletonKey key) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");
        try {
            ModelPartSourceRigAdapter.Capture capture = ModelPartSourceRigAdapter.capture(model, key);
            HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(capture.snapshot());
            HumanoidSkeletonDescriptor descriptor = HumanoidSkeletonResolver.resolve(capture.snapshot(), analysis);
            HumanoidSkinWeightResolver weights = new HumanoidSkinWeightResolver(capture.snapshot(), analysis, descriptor);
            SkinnedMesh mesh = bake(capture, weights);
            return mesh == null
                    ? Result.unsupported("mesh bake produced no body vertices")
                    : Result.supported(descriptor, mesh);
        } catch (Throwable throwable) {
            return Result.unsupported("ModelPart humanoid mesh bake failed: " + throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : " (" + throwable.getMessage() + ")"));
        }
    }

    private static SkinnedMesh bake(
            ModelPartSourceRigAdapter.Capture capture,
            HumanoidSkinWeightResolver weights) throws ReflectiveOperationException {
        List<SingleGroupVertexBuilder> vertices = new ArrayList<>();
        Map<MeshPartDefinition, IntList> indices = new LinkedHashMap<>();

        for (ModelPartSourceRigAdapter.CubeEntry entry : capture.cubes()) {
            if (!entry.attachment()) {
                bakeCube(entry, weights, vertices, indices);
            }
        }

        return vertices.isEmpty()
                ? null
                : SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
    }

    private static void bakeCube(
            ModelPartSourceRigAdapter.CubeEntry entry,
            HumanoidSkinWeightResolver weights,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) throws ReflectiveOperationException {
        Object polygons = cubePolygonsField().get(entry.cube());
        if (polygons == null || !polygons.getClass().isArray()) {
            return;
        }

        for (int q = 0; q < Array.getLength(polygons); q++) {
            Object polygon = Array.get(polygons, q);
            if (polygon == null) {
                continue;
            }

            PolygonFields polygonFields = polygonFields(polygon.getClass());
            Object sourceVertices = polygonFields.vertices().get(polygon);
            Object normalValue = polygonFields.normal().get(polygon);
            if (sourceVertices == null
                    || !sourceVertices.getClass().isArray()
                    || Array.getLength(sourceVertices) != 4
                    || !(normalValue instanceof Vector3f sourceNormal)) {
                continue;
            }

            Vector3f normal = new Vector3f(sourceNormal);
            normal.mul(entry.normal());
            if (normal.lengthSquared() > 1.0E-8F) {
                normal.normalize();
            }

            int first = vertices.size();
            List<SingleGroupVertexBuilder> built = new ArrayList<>(4);
            for (int v = 0; v < 4; v++) {
                Object sourceVertex = Array.get(sourceVertices, v);
                if (sourceVertex == null) {
                    built.clear();
                    break;
                }

                VertexFields vertexFields = vertexFields(sourceVertex.getClass());
                Object positionValue = vertexFields.position().get(sourceVertex);
                if (!(positionValue instanceof Vector3f sourcePosition)) {
                    built.clear();
                    break;
                }

                Vector4f position4 = new Vector4f(
                        sourcePosition.x() / 16.0F,
                        sourcePosition.y() / 16.0F,
                        sourcePosition.z() / 16.0F,
                        1.0F
                );
                position4.mul(entry.pose());
                SkeletonVec3 position = new SkeletonVec3(position4.x(), position4.y(), position4.z());
                HumanoidSkinWeightResolver.SkinWeights skin = weights.weights(entry.nodeId(), position);

                built.add(new SingleGroupVertexBuilder()
                        .setPosition(new Vec3f(position.x(), position.y(), position.z()))
                        .setNormal(new Vec3f(normal.x(), normal.y(), normal.z()))
                        .setTextureCoordinate(new Vec2f(
                                vertexFields.u().getFloat(sourceVertex),
                                vertexFields.v().getFloat(sourceVertex)
                        ))
                        .setEffectiveJointIDs(new Vec3f(skin.firstJoint(), skin.secondJoint(), 0.0F))
                        .setEffectiveJointWeights(new Vec3f(skin.firstWeight(), skin.secondWeight(), 0.0F))
                        .setEffectiveJointNumber(skin.count()));
            }

            if (built.size() != 4) {
                continue;
            }
            vertices.addAll(built);
            triangulateQuad(indices, first);
        }
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

    private static Field cubePolygonsField() throws ReflectiveOperationException {
        if (cubePolygonsField != null) {
            return cubePolygonsField;
        }
        synchronized (ModelPartHumanoidMeshAdapter.class) {
            if (cubePolygonsField == null) {
                for (Field field : ModelPart.Cube.class.getDeclaredFields()) {
                    if (field.getType().isArray()) {
                        field.setAccessible(true);
                        cubePolygonsField = field;
                        break;
                    }
                }
                if (cubePolygonsField == null) {
                    throw new NoSuchFieldException("ModelPart.Cube polygon array");
                }
            }
            return cubePolygonsField;
        }
    }

    private static synchronized PolygonFields polygonFields(Class<?> type) throws ReflectiveOperationException {
        PolygonFields cached = POLYGON_FIELDS.get(type);
        if (cached != null) {
            return cached;
        }

        Field vertices = null;
        Field normal = null;
        for (Field field : type.getDeclaredFields()) {
            if (field.getType().isArray()) {
                vertices = field;
            } else if (Vector3f.class.isAssignableFrom(field.getType())) {
                normal = field;
            }
        }
        if (vertices == null || normal == null) {
            throw new NoSuchFieldException("ModelPart polygon fields");
        }
        vertices.setAccessible(true);
        normal.setAccessible(true);
        PolygonFields result = new PolygonFields(vertices, normal);
        POLYGON_FIELDS.put(type, result);
        return result;
    }

    private static synchronized VertexFields vertexFields(Class<?> type) throws ReflectiveOperationException {
        VertexFields cached = VERTEX_FIELDS.get(type);
        if (cached != null) {
            return cached;
        }

        Field position = null;
        Field namedU = null;
        Field namedV = null;
        List<Field> floats = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (Vector3f.class.isAssignableFrom(field.getType())) {
                position = field;
            } else if (field.getType() == float.class) {
                floats.add(field);
                if ("u".equals(field.getName())) {
                    namedU = field;
                } else if ("v".equals(field.getName())) {
                    namedV = field;
                }
            }
        }
        if (position == null || floats.size() < 2) {
            throw new NoSuchFieldException("ModelPart vertex fields");
        }

        Field u = namedU != null ? namedU : floats.get(0);
        Field v = namedV != null ? namedV : floats.get(1);
        position.setAccessible(true);
        u.setAccessible(true);
        v.setAccessible(true);
        VertexFields result = new VertexFields(position, u, v);
        VERTEX_FIELDS.put(type, result);
        return result;
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

    private record PolygonFields(Field vertices, Field normal) {
    }

    private record VertexFields(Field position, Field u, Field v) {
    }

    private record RuntimeModelPart(String partName) implements MeshPartDefinition {
        @Override
        public Mesh.RenderProperties renderProperties() {
            return null;
        }

        @Override
        public Supplier<OpenMatrix4f> getModelPartAnimationProvider() {
            return () -> null;
        }
    }

    private ModelPartHumanoidMeshAdapter() {
    }
}
