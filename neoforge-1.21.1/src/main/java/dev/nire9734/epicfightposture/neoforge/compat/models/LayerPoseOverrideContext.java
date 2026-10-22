package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.SecondaryDeformationPolicy;
import dev.nire9734.epicfightposture.compat.humanoid.rig.ConservativeHumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Supplies Epic Fight transforms while EFPI replays source {@code RenderLayer}s.
 *
 * <p>Many modded layers render the source parent model a second time for
 * translucent/emissive geometry. Others own a second model instance, run that
 * model's {@code setupAnim()} inside the layer, and then render it. A Root-only
 * transform cannot keep hand-held descendants in either case. This context maps
 * every source part through the same framework-neutral humanoid analysis used by
 * the runtime mesh and replaces only the part transform at render time.</p>
 *
 * <p>Secondary-model semantic ownership comes from the secondary source hierarchy,
 * but all rigid ownership decisions are evaluated against the primary body's
 * {@link HumanoidSkeletonDescriptor}. A clothing model therefore cannot invent a
 * different elbow, knee or spine location from the body it covers.</p>
 */
public final class LayerPoseOverrideContext {
    private static final float MATRIX_EPSILON = 1.0E-8F;
    private static final String LIONFISH_MODEL_CLASS =
            "com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel";
    private static final String LIONFISH_ADAPTER_CLASS =
            "dev.nire9734.epicfightposture.neoforge.compat.models.LionfishSourceRigAdapter";

    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private static final int MAX_PLANS_PER_MODEL = 64;
    private static final Map<Object, Map<DescriptorCacheKey, ModelPlan>> MODEL_PLANS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Renderer/armature reload invalidates source-layer retargeting plans. */
    static void clearCache() {
        synchronized (MODEL_PLANS) {
            MODEL_PLANS.clear();
        }
    }
    private static final ClassValue<Field[]> DECLARED_FIELDS = new ClassValue<>() {
        @Override
        protected Field[] computeValue(Class<?> type) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.trySetAccessible();
                } catch (Throwable ignored) {
                    // Optional private layer fields are handled conservatively below.
                }
            }
            return fields;
        }
    };

    public static Scope open(
            LivingEntity entity,
            EntityModel<?> parentModel,
            Iterable<?> layers,
            Armature armature,
            HumanoidSkeletonDescriptor primaryDescriptor) {
        Context previous = ACTIVE.get();
        Context current;
        try {
            current = primaryDescriptor == null ? null : new Context(entity, armature, primaryDescriptor);
            if (current != null) {
                current.registerModel(parentModel);
                if (layers != null) {
                    for (Object layer : layers) {
                        current.registerLayerModels(layer);
                    }
                }
            }
        } catch (Throwable ignored) {
            current = null;
        }

        if (current == null || current.localOverrides.isEmpty()) {
            return new Scope(previous, false);
        }

        ACTIVE.set(current);
        return new Scope(previous, true);
    }

    /** Called by the ModelPart/Lionfish mixins at translateAndRotate HEAD. */
    public static boolean applyIfPresent(PoseStack poseStack, Object sourcePart) {
        Context context = ACTIVE.get();
        if (context == null || poseStack == null || sourcePart == null) {
            return false;
        }

        Matrix4f local = context.localOverride(sourcePart);
        if (local == null) {
            return false;
        }

        poseStack.mulPose(new Matrix4f(local));

        Matrix3f normalTransform = new Matrix3f(local);
        float determinant = normalTransform.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > MATRIX_EPSILON) {
            normalTransform.invert().transpose();
            poseStack.last().normal().mul(normalTransform);
        }
        return true;
    }

    public static final class Scope implements AutoCloseable {
        private final Context previous;
        private final boolean installed;
        private boolean closed;

        private Scope(Context previous, boolean installed) {
            this.previous = previous;
            this.installed = installed;
        }

        @Override
        public void close() {
            if (closed || !installed) {
                closed = true;
                return;
            }
            closed = true;
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    private static final class Context {
        private final LivingEntity entity;
        private final Armature armature;
        private final HumanoidSkeletonDescriptor primaryDescriptor;
        private final DescriptorCacheKey descriptorCacheKey;
        private final OpenMatrix4f[] poseMatrices;
        private final Matrix4f inverseExternalTransform;
        private final IdentityHashMap<Object, Matrix4f> localOverrides = new IdentityHashMap<>();
        private final Set<Object> attemptedModels = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Object> attemptedParts = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Integer, Matrix4f> skinMatrices = new HashMap<>();

        private Context(
                LivingEntity entity,
                Armature armature,
                HumanoidSkeletonDescriptor primaryDescriptor) {
            this.entity = entity;
            this.armature = armature;
            this.primaryDescriptor = primaryDescriptor;
            this.descriptorCacheKey = new DescriptorCacheKey(
                    primaryDescriptor.key(),
                    descriptorFingerprint(primaryDescriptor)
            );
            this.poseMatrices = armature.getPoseMatrices();

            int rootId = armature.rootJoint.getId();
            if (rootId < 0 || rootId >= poseMatrices.length || poseMatrices[rootId] == null) {
                throw new IllegalArgumentException("Armature exposes no usable Root pose matrix");
            }

            Matrix4f external = OpenMatrix4f.exportToMojangMatrix(poseMatrices[rootId]);
            external.scale(-1.0F, -1.0F, 1.0F);
            this.inverseExternalTransform = new Matrix4f(external).invert();
        }

        private void registerLayerModels(Object layer) {
            if (layer == null) {
                return;
            }
            Class<?> type = layer.getClass();
            while (type != null && type != Object.class) {
                for (Field field : DECLARED_FIELDS.get(type)) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    try {
                        if (!field.canAccess(layer)) {
                            field.setAccessible(true);
                        }
                        Object value = field.get(layer);
                        if (value instanceof EntityModel<?> model) {
                            registerModel(model);
                        }
                    } catch (Throwable ignored) {
                        // Optional implementation detail. Parent-model replay is
                        // still available when a private layer field is inaccessible.
                    }
                }
                type = type.getSuperclass();
            }
        }

        private void registerModel(Object model) {
            if (model == null || !attemptedModels.add(model)) {
                return;
            }
            try {
                installPlan(modelPlan(model));
            } catch (Throwable ignored) {
                // Secondary layer models are optional. The outer Root fallback
                // remains active when a model cannot be normalized safely.
            }
        }

        private ModelPlan modelPlan(Object model) throws Exception {
            synchronized (MODEL_PLANS) {
                Map<DescriptorCacheKey, ModelPlan> byDescriptor = MODEL_PLANS.get(model);
                if (byDescriptor != null) {
                    ModelPlan cached = byDescriptor.get(descriptorCacheKey);
                    if (cached != null) {
                        return cached;
                    }
                }
            }

            ModelPlan built = buildModelPlan(model, keyFor(model));
            synchronized (MODEL_PLANS) {
                Map<DescriptorCacheKey, ModelPlan> byDescriptor =
                        MODEL_PLANS.computeIfAbsent(
                                model, ignored -> new BoundedLruCache<>(MAX_PLANS_PER_MODEL));
                ModelPlan raced = byDescriptor.get(descriptorCacheKey);
                if (raced != null) {
                    return raced;
                }
                byDescriptor.put(descriptorCacheKey, built);
            }
            return built;
        }

        private ModelPlan buildModelPlan(Object model, SkeletonKey key) throws Exception {
            if (model instanceof HierarchicalModel<?> hierarchicalModel) {
                ModelPartSourceRigAdapter.Capture capture = ModelPartSourceRigAdapter.capture(
                        hierarchicalModel,
                        key
                );
                HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE
                        .analyze(capture.snapshot());
                return buildPlan(capture.snapshot(), analysis, capture.sourceNodes(), primaryDescriptor);
            }

            if (isLionfishModel(model)) {
                return buildLionfishPlan(model, key);
            }
            return ModelPlan.EMPTY;
        }

        @SuppressWarnings("unchecked")
        private ModelPlan buildLionfishPlan(Object model, SkeletonKey key) throws Exception {
            Class<?> adapterType = Class.forName(
                    LIONFISH_ADAPTER_CLASS,
                    true,
                    LayerPoseOverrideContext.class.getClassLoader()
            );
            Method captureMethod = null;
            for (Method method : adapterType.getDeclaredMethods()) {
                if (method.getName().equals("capture") && method.getParameterCount() == 2) {
                    captureMethod = method;
                    break;
                }
            }
            if (captureMethod == null) {
                return ModelPlan.EMPTY;
            }
            if (!captureMethod.canAccess(null)) {
                captureMethod.setAccessible(true);
            }

            Object capture = captureMethod.invoke(null, model, key);
            Method snapshotAccessor = capture.getClass().getMethod("snapshot");
            Method sourceNodesAccessor = capture.getClass().getMethod("sourceNodes");
            SourceRigSnapshot snapshot = (SourceRigSnapshot) snapshotAccessor.invoke(capture);
            Map<String, ?> sourceNodes = (Map<String, ?>) sourceNodesAccessor.invoke(capture);
            HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(snapshot);
            return buildPlan(snapshot, analysis, sourceNodes, primaryDescriptor);
        }

        private void installPlan(ModelPlan plan) {
            if (plan == null || plan.parts().isEmpty()) {
                return;
            }

            Map<String, Matrix4f> desiredAbsolute = new HashMap<>();
            for (PlannedPart planned : plan.parts()) {
                Matrix4f targetAbsolute = new Matrix4f(skinMatrix(planned.jointId()))
                        .mul(planned.bindAbsolute());
                Matrix4f sourceAbsolute = new Matrix4f(inverseExternalTransform).mul(targetAbsolute);
                desiredAbsolute.put(planned.nodeId(), sourceAbsolute);

                Object sourcePart = planned.sourcePart().get();
                if (sourcePart == null) {
                    continue;
                }

                Matrix4f local = new Matrix4f(sourceAbsolute);
                if (planned.parentId() != null) {
                    Matrix4f parentAbsolute = desiredAbsolute.get(planned.parentId());
                    if (parentAbsolute != null) {
                        local = new Matrix4f(parentAbsolute).invert().mul(sourceAbsolute);
                    }
                }
                localOverrides.put(sourcePart, local);
            }
        }

        private Matrix4f localOverride(Object sourcePart) {
            Matrix4f local = localOverrides.get(sourcePart);
            if (local != null || !attemptedParts.add(sourcePart)) {
                return local;
            }

            try {
                Method getModel = sourcePart.getClass().getMethod("getModel");
                Object model = getModel.invoke(sourcePart);
                if (model != null && isLionfishModel(model)) {
                    registerModel(model);
                }
            } catch (Throwable ignored) {
                // Minecraft ModelPart has no model back-reference.
            }
            return localOverrides.get(sourcePart);
        }

        private Matrix4f skinMatrix(int requestedJointId) {
            int jointId = requestedJointId;
            if (jointId < 0 || jointId >= poseMatrices.length || poseMatrices[jointId] == null
                    || armature.searchJointById(jointId) == null) {
                jointId = armature.rootJoint.getId();
            }
            final int resolvedJointId = jointId;
            return skinMatrices.computeIfAbsent(resolvedJointId, id -> {
                Joint joint = armature.searchJointById(id);
                OpenMatrix4f skin = new OpenMatrix4f(poseMatrices[id]);
                if (joint != null) {
                    skin.mulBack(joint.getToOrigin());
                }
                return OpenMatrix4f.exportToMojangMatrix(skin);
            });
        }

        private SkeletonKey keyFor(Object model) {
            ResourceLocation entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return new SkeletonKey(entityId.toString(), model.getClass().getName() + "#render_layer");
        }
    }

    private static ModelPlan buildPlan(
            SourceRigSnapshot snapshot,
            HumanoidSemanticAnalysis analysis,
            Map<String, ?> sourceNodes,
            HumanoidSkeletonDescriptor primaryDescriptor) {
        HumanoidSkinWeightResolver weights =
                new HumanoidSkinWeightResolver(snapshot, analysis, primaryDescriptor);
        Map<String, Matrix4f> bindAbsolute = buildFullBindMatrices(snapshot, sourceNodes);
        List<PlannedPart> planned = new ArrayList<>();

        for (SourceNodeSnapshot node : orderedNodes(snapshot)) {
            Object sourcePart = sourceNodes.get(node.id());
            Matrix4f bind = bindAbsolute.get(node.id());
            if (sourcePart == null || bind == null) {
                continue;
            }

            boolean directSemanticSource = analysis.candidates().values().stream().anyMatch(candidate ->
                    candidate.origin() == JointOrigin.SOURCE
                            && candidate.sourceNodeId().filter(node.id()::equals).isPresent());
            int jointId = rigidJointForNode(node, directSemanticSource, weights);
            planned.add(new PlannedPart(
                    new WeakReference<>(sourcePart),
                    node.id(),
                    node.parentId().orElse(null),
                    new Matrix4f(bind),
                    jointId
            ));
        }
        return planned.isEmpty() ? ModelPlan.EMPTY : new ModelPlan(List.copyOf(planned));
    }

    private static int rigidJointForNode(
            SourceNodeSnapshot node,
            boolean directSemanticSource,
            HumanoidSkinWeightResolver weights) {
        if (!directSemanticSource && node.geometryBounds().isPresent()) {
            List<HumanoidSkinWeightResolver.SkinWeights> samples = new ArrayList<>();
            for (SkeletonVec3 sample : boundsSamples(node.geometryBounds().orElseThrow())) {
                samples.add(weights.weights(node.id(), sample));
            }
            var decision = SecondaryDeformationPolicy.classify(samples);
            if (decision.isPresent() && decision.get().rigid()) {
                return decision.get().rigidJointId();
            }
        }

        SkeletonVec3 anchorPosition = rigidAnchorPosition(node, directSemanticSource);
        return SecondaryDeformationPolicy.dominantJoint(weights.weights(node.id(), anchorPosition));
    }

    static SkeletonVec3 rigidAnchorPosition(SourceNodeSnapshot node, boolean directSemanticSource) {
        if (directSemanticSource) {
            return node.bindModelTransform().position();
        }
        return node.geometryBounds()
                .map(BodyBounds::center)
                .orElse(node.bindModelTransform().position());
    }

    private static List<SkeletonVec3> boundsSamples(BodyBounds bounds) {
        List<SkeletonVec3> samples = new ArrayList<>(9);
        float[] xs = {bounds.min().x(), bounds.max().x()};
        float[] ys = {bounds.min().y(), bounds.max().y()};
        float[] zs = {bounds.min().z(), bounds.max().z()};
        for (float x : xs) {
            for (float y : ys) {
                for (float z : zs) {
                    samples.add(new SkeletonVec3(x, y, z));
                }
            }
        }
        samples.add(bounds.center());
        return samples;
    }

    /** Rebuilds the same full bind matrices used by the framework adapters. */
    private static Map<String, Matrix4f> buildFullBindMatrices(
            SourceRigSnapshot snapshot,
            Map<String, ?> sourceNodes) {
        Map<String, Matrix4f> result = new HashMap<>();
        for (SourceNodeSnapshot node : orderedNodes(snapshot)) {
            Object sourcePart = sourceNodes.get(node.id());
            if (sourcePart == null) {
                continue;
            }

            Matrix4f parent = node.parentId()
                    .map(result::get)
                    .map(Matrix4f::new)
                    .orElseGet(LayerPoseOverrideContext::canonicalModelMatrix);
            Matrix4f local = sourceBindLocalMatrix(sourcePart);
            if (local == null) {
                continue;
            }
            result.put(node.id(), parent.mul(local));
        }
        return result;
    }

    private static List<SourceNodeSnapshot> orderedNodes(SourceRigSnapshot snapshot) {
        List<SourceNodeSnapshot> ordered = new ArrayList<>(snapshot.nodes().values());
        ordered.sort(Comparator.comparingInt(node -> snapshot.depth(node.id())));
        return ordered;
    }

    private static Matrix4f sourceBindLocalMatrix(Object sourcePart) {
        if (sourcePart instanceof ModelPart part) {
            PartPose pose = part.getInitialPose();
            return new Matrix4f()
                    .translation(pose.x / 16.0F, pose.y / 16.0F, pose.z / 16.0F)
                    .rotate(new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot))
                    .scale(part.xScale, part.yScale, part.zScale);
        }

        try {
            float defaultScaleX = readFloat(sourcePart, "defaultscaleX");
            float defaultScaleY = readFloat(sourcePart, "defaultscaleY");
            float defaultScaleZ = readFloat(sourcePart, "defaultscaleZ");
            boolean stored = defaultScaleX != 0.0F || defaultScaleY != 0.0F || defaultScaleZ != 0.0F;

            float x = readFloat(sourcePart, stored ? "defaultPositionX" : "rotationPointX");
            float y = readFloat(sourcePart, stored ? "defaultPositionY" : "rotationPointY");
            float z = readFloat(sourcePart, stored ? "defaultPositionZ" : "rotationPointZ");
            float rx = readFloat(sourcePart, stored ? "defaultRotationX" : "rotateAngleX");
            float ry = readFloat(sourcePart, stored ? "defaultRotationY" : "rotateAngleY");
            float rz = readFloat(sourcePart, stored ? "defaultRotationZ" : "rotateAngleZ");
            float sx = stored ? defaultScaleX : readFloat(sourcePart, "xScale");
            float sy = stored ? defaultScaleY : readFloat(sourcePart, "yScale");
            float sz = stored ? defaultScaleZ : readFloat(sourcePart, "zScale");

            return new Matrix4f()
                    .translation(x / 16.0F, y / 16.0F, z / 16.0F)
                    .rotate(new Quaternionf().rotationZYX(rz, ry, rx))
                    .scale(sx, sy, sz);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float readFloat(Object instance, String name) throws ReflectiveOperationException {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                if (!field.canAccess(instance)) {
                    field.setAccessible(true);
                }
                return field.getFloat(instance);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** Matches both source adapters' model-space basis. */
    private static Matrix4f canonicalModelMatrix() {
        return new Matrix4f()
                .scale(-1.0F, -1.0F, 1.0F)
                .translate(0.0F, -1.5F, 0.0F);
    }

    private static boolean isLionfishModel(Object model) {
        Class<?> type = model == null ? null : model.getClass();
        while (type != null) {
            if (LIONFISH_MODEL_CLASS.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static int descriptorFingerprint(HumanoidSkeletonDescriptor descriptor) {
        int hash = 1;
        for (HumanoidJoint joint : HumanoidJoint.values()) {
            try {
                SkeletonVec3 p = descriptor.bindModelPosition(joint);
                hash = 31 * hash + Float.floatToIntBits(p.x());
                hash = 31 * hash + Float.floatToIntBits(p.y());
                hash = 31 * hash + Float.floatToIntBits(p.z());
            } catch (IllegalArgumentException ignored) {
                hash = 31 * hash;
            }
        }
        return hash;
    }

    private record DescriptorCacheKey(SkeletonKey key, int fingerprint) {
    }

    private record PlannedPart(
            WeakReference<Object> sourcePart,
            String nodeId,
            String parentId,
            Matrix4f bindAbsolute,
            int jointId) {
    }

    private record ModelPlan(List<PlannedPart> parts) {
        private static final ModelPlan EMPTY = new ModelPlan(List.of());
    }

    private LayerPoseOverrideContext() {
    }
}
