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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One-time Lionfish/Citadel-style humanoid -> Epic Fight SkinnedMesh baker.
 *
 * <p>The source model is evaluated only in its authored bind pose. Geometry is
 * converted to EFPI model space and then assigned semantic BIPED skin weights.
 * Source animation axes are deliberately not imported: runtime animation is owned
 * by the target-proportioned Epic Fight HumanoidArmature.</p>
 */
public final class LionfishHumanoidMeshAdapter {
    private static final MeshPartDefinition PART = new RuntimeLionfishPart("lionfish_humanoid");
    private static volatile ReflectionAccess reflectionAccess;

    public static Result analyseAndBake(AdvancedEntityModel<?> model, SkeletonKey key) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(key, "key");

        try {
            LionfishSourceRigAdapter.Capture capture = LionfishSourceRigAdapter.capture(model, key);
            HumanoidSemanticAnalysis analysis =
                    ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(capture.snapshot());
            HumanoidSkeletonDescriptor descriptor =
                    HumanoidSkeletonResolver.resolve(capture.snapshot(), analysis);
            HumanoidSkinWeightResolver weights =
                    new HumanoidSkinWeightResolver(capture.snapshot(), analysis, descriptor);

            SkinnedMesh mesh = bake(model, capture, weights);
            if (mesh == null) {
                return Result.unsupported("mesh bake produced no vertices");
            }
            return Result.supported(descriptor, mesh);
        } catch (Throwable throwable) {
            return Result.unsupported(
                    "Lionfish humanoid mesh bake failed: "
                            + throwable.getClass().getSimpleName()
                            + (throwable.getMessage() == null ? "" : " (" + throwable.getMessage() + ")")
            );
        }
    }

    private static SkinnedMesh bake(
            AdvancedEntityModel<?> model,
            LionfishSourceRigAdapter.Capture capture,
            HumanoidSkinWeightResolver weights) throws ReflectiveOperationException {
        IdentityHashMap<AdvancedModelBox, String> nodeIds = new IdentityHashMap<>();
        capture.sourceNodes().forEach((id, node) -> nodeIds.put(node, id));

        List<AdvancedModelBox> roots = new ArrayList<>();
        for (AdvancedModelBox part : model.getAllParts()) {
            if (part != null && part.getParent() == null) {
                roots.add(part);
            }
        }

        List<SingleGroupVertexBuilder> vertices = new ArrayList<>();
        Map<MeshPartDefinition, IntList> indices = new LinkedHashMap<>();
        PoseStack stack = new PoseStack();
        stack.scale(-1.0F, -1.0F, 1.0F);

        for (AdvancedModelBox root : roots) {
            bakePart(root, stack, nodeIds, weights, vertices, indices);
        }

        return vertices.isEmpty()
                ? null
                : SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
    }

    private static void bakePart(
            AdvancedModelBox part,
            PoseStack stack,
            IdentityHashMap<AdvancedModelBox, String> nodeIds,
            HumanoidSkinWeightResolver weights,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) throws ReflectiveOperationException {
        if (part == null || !part.showModel) {
            return;
        }

        String sourceNodeId = nodeIds.get(part);
        if (sourceNodeId == null) {
            return;
        }

        BindValues bind = bindValues(part);
        stack.pushPose();
        stack.translate(
                bind.positionX() / 16.0F,
                bind.positionY() / 16.0F,
                bind.positionZ() / 16.0F
        );
        stack.mulPose(new Quaternionf().rotationZYX(
                bind.rotationZ(), bind.rotationY(), bind.rotationX()));
        stack.scale(bind.scaleX(), bind.scaleY(), bind.scaleZ());

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
        if (cube == null) {
            return;
        }

        ReflectionAccess reflection = reflection();
        Object quadsArray = reflection.quadsField().get(cube);
        if (quadsArray == null) {
            return;
        }

        int quadCount = Array.getLength(quadsArray);
        for (int q = 0; q < quadCount; q++) {
            Object quad = Array.get(quadsArray, q);
            if (quad == null) {
                continue;
            }

            Object verticesArray = reflection.vertexPositionsField(quad.getClass()).get(quad);
            Object normalValue = reflection.normalField(quad.getClass()).get(quad);
            if (verticesArray == null || normalValue == null || Array.getLength(verticesArray) != 4
                    || !(normalValue instanceof Vector3f sourceNormal)) {
                continue;
            }

            Vector3f normal = new Vector3f(sourceNormal);
            normal.mul(stack.last().normal());
            if (normal.lengthSquared() > 1.0E-8F) {
                normal.normalize();
            }

            int first = vertices.size();
            boolean complete = true;
            List<SingleGroupVertexBuilder> quadVertices = new ArrayList<>(4);
            for (int v = 0; v < 4; v++) {
                Object sourceVertex = Array.get(verticesArray, v);
                if (sourceVertex == null) {
                    complete = false;
                    break;
                }

                Object positionValue = reflection.positionField(sourceVertex.getClass()).get(sourceVertex);
                if (!(positionValue instanceof Vector3f sourcePosition)) {
                    complete = false;
                    break;
                }
                float u = reflection.textureUField(sourceVertex.getClass()).getFloat(sourceVertex);
                float texV = reflection.textureVField(sourceVertex.getClass()).getFloat(sourceVertex);

                Vector4f position4 = new Vector4f(
                        sourcePosition.x() / 16.0F,
                        sourcePosition.y() / 16.0F,
                        sourcePosition.z() / 16.0F,
                        1.0F
                );
                position4.mul(stack.last().pose());
                SkeletonVec3 position = new SkeletonVec3(position4.x(), position4.y(), position4.z());
                HumanoidSkinWeightResolver.SkinWeights skin = weights.weights(sourceNodeId, position);

                quadVertices.add(new SingleGroupVertexBuilder()
                        .setPosition(new Vec3f(position.x(), position.y(), position.z()))
                        .setNormal(new Vec3f(normal.x(), normal.y(), normal.z()))
                        .setTextureCoordinate(new Vec2f(u, texV))
                        .setEffectiveJointIDs(new Vec3f(
                                skin.firstJoint(), skin.secondJoint(), 0.0F))
                        .setEffectiveJointWeights(new Vec3f(
                                skin.firstWeight(), skin.secondWeight(), 0.0F))
                        .setEffectiveJointNumber(skin.count()));
            }

            if (!complete || quadVertices.size() != 4) {
                continue;
            }
            vertices.addAll(quadVertices);
            triangulateQuad(indices, first);
        }
    }

    private static void triangulateQuad(Map<MeshPartDefinition, IntList> indices, int first) {
        IntList list = indices.computeIfAbsent(PART, ignored -> new IntArrayList());
        int second = first + 1;
        int third = first + 2;
        int fourth = first + 3;

        addIndexTriplet(list, first);
        addIndexTriplet(list, second);
        addIndexTriplet(list, fourth);
        addIndexTriplet(list, fourth);
        addIndexTriplet(list, second);
        addIndexTriplet(list, third);
    }

    private static void addIndexTriplet(IntList list, int index) {
        list.add(index);
        list.add(index);
        list.add(index);
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

    private static ReflectionAccess reflection() throws ReflectiveOperationException {
        ReflectionAccess cached = reflectionAccess;
        if (cached != null) {
            return cached;
        }
        synchronized (LionfishHumanoidMeshAdapter.class) {
            cached = reflectionAccess;
            if (cached == null) {
                Field quads = LionfishModelRenderUtils.ModelBox.class.getDeclaredField("quads");
                quads.setAccessible(true);
                cached = new ReflectionAccess(quads);
                reflectionAccess = cached;
            }
            return cached;
        }
    }

    public record Result(
            boolean supported,
            HumanoidSkeletonDescriptor descriptor,
            SkinnedMesh mesh,
            String reason) {
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
            float scaleX, float scaleY, float scaleZ) {
    }

    private static final class ReflectionAccess {
        private final Field quadsField;
        private final Map<Class<?>, QuadFields> quadFields = new HashMap<>();
        private final Map<Class<?>, VertexFields> vertexFields = new HashMap<>();

        private ReflectionAccess(Field quadsField) {
            this.quadsField = quadsField;
        }

        Field quadsField() {
            return quadsField;
        }

        Field vertexPositionsField(Class<?> quadClass) throws ReflectiveOperationException {
            return quadFields(quadClass).vertices();
        }

        Field normalField(Class<?> quadClass) throws ReflectiveOperationException {
            return quadFields(quadClass).normal();
        }

        Field positionField(Class<?> vertexClass) throws ReflectiveOperationException {
            return vertexFields(vertexClass).position();
        }

        Field textureUField(Class<?> vertexClass) throws ReflectiveOperationException {
            return vertexFields(vertexClass).textureU();
        }

        Field textureVField(Class<?> vertexClass) throws ReflectiveOperationException {
            return vertexFields(vertexClass).textureV();
        }

        private synchronized QuadFields quadFields(Class<?> type) throws ReflectiveOperationException {
            QuadFields fields = quadFields.get(type);
            if (fields == null) {
                Field vertices = type.getDeclaredField("vertexPositions");
                Field normal = type.getDeclaredField("normal");
                vertices.setAccessible(true);
                normal.setAccessible(true);
                fields = new QuadFields(vertices, normal);
                quadFields.put(type, fields);
            }
            return fields;
        }

        private synchronized VertexFields vertexFields(Class<?> type) throws ReflectiveOperationException {
            VertexFields fields = vertexFields.get(type);
            if (fields == null) {
                Field position = type.getDeclaredField("position");
                Field textureU = type.getDeclaredField("textureU");
                Field textureV = type.getDeclaredField("textureV");
                position.setAccessible(true);
                textureU.setAccessible(true);
                textureV.setAccessible(true);
                fields = new VertexFields(position, textureU, textureV);
                vertexFields.put(type, fields);
            }
            return fields;
        }
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
