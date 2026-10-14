package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.ConservativeHumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SemanticJointCandidate;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
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
 * <p>No entity id, mod id, renderer class, or mob name is special-cased. Optional
 * Lionfish support is reached reflectively so the compatibility library remains
 * optional at runtime.</p>
 */
public final class LayerPoseOverrideContext {
    private static final float MATRIX_EPSILON = 1.0E-8F;
    private static final String LIONFISH_MODEL_CLASS =
            "com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel";
    private static final String LIONFISH_ADAPTER_CLASS =
            "dev.nire9734.epicfightposture.compat.humanoid.client.LionfishSourceRigAdapter";

    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();

    public static Scope open(
            LivingEntity entity,
            EntityModel<?> parentModel,
            Object layer,
            Armature armature) {
        Context previous = ACTIVE.get();
        Context current;
        try {
            current = new Context(entity, armature);
            current.registerModel(parentModel);
            current.registerLayerModels(layer);
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

        poseStack.mulPoseMatrix(new Matrix4f(local));

        // Minecraft 1.20.1 PoseStack.mulPoseMatrix only changes the position
        // matrix. translateAndRotate normally keeps normals in sync as well, so
        // reproduce the inverse-transpose normal transform for our replacement.
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
        private final OpenMatrix4f[] poseMatrices;
        private final Matrix4f inverseExternalTransform;
        private final IdentityHashMap<Object, Matrix4f> localOverrides = new IdentityHashMap<>();
        private final Set<Object> attemptedModels = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Object> attemptedParts = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Integer, Matrix4f> skinMatrices = new HashMap<>();

        private Context(LivingEntity entity, Armature armature) {
            this.entity = entity;
            this.armature = armature;
            this.poseMatrices = armature.getPoseMatrices();

            int rootId = armature.rootJoint.getId();
            if (rootId < 0 || rootId >= poseMatrices.length || poseMatrices[rootId] == null) {
                throw new IllegalArgumentException("Armature exposes no usable Root pose matrix");
            }

            // LivingRenderLayerBridge leaves its historical generic fallback in
            // place: animated Root followed by the source-model axis flip. Exact
            // per-part transforms below are expressed underneath that basis so an
            // unsupported layer still falls back to the old behavior unchanged.
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
                for (Field field : type.getDeclaredFields()) {
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
                if (model instanceof HierarchicalModel<?> hierarchicalModel) {
                    ModelPartSourceRigAdapter.Capture capture = ModelPartSourceRigAdapter.capture(
                            hierarchicalModel,
                            keyFor(model)
                    );
                    HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE
                            .analyze(capture.snapshot());
                    registerCapture(capture.snapshot(), analysis, capture.sourceNodes());
                    return;
                }

                if (isLionfishModel(model)) {
                    registerLionfishModel(model);
                }
            } catch (Throwable ignored) {
                // Secondary layer models are optional. The outer Root fallback
                // remains active when a model cannot be normalized safely.
            }
        }

        private Matrix4f localOverride(Object sourcePart) {
            Matrix4f local = localOverrides.get(sourcePart);
            if (local != null || !attemptedParts.add(sourcePart)) {
                return local;
            }

            // Lionfish parts retain a model back-reference. This catches a model
            // constructed lazily inside a layer even when it was not discoverable
            // as an EntityModel field when the replay scope was opened.
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

        @SuppressWarnings("unchecked")
        private void registerLionfishModel(Object model) throws Exception {
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
                return;
            }
            if (!captureMethod.canAccess(null)) {
                captureMethod.setAccessible(true);
            }

            Object capture = captureMethod.invoke(null, model, keyFor(model));
            Method snapshotAccessor = capture.getClass().getMethod("snapshot");
            Method sourceNodesAccessor = capture.getClass().getMethod("sourceNodes");
            SourceRigSnapshot snapshot = (SourceRigSnapshot) snapshotAccessor.invoke(capture);
            Map<String, ?> sourceNodes = (Map<String, ?>) sourceNodesAccessor.invoke(capture);
            HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(snapshot);
            registerCapture(snapshot, analysis, sourceNodes);
        }

        private void registerCapture(
                SourceRigSnapshot snapshot,
                HumanoidSemanticAnalysis analysis,
                Map<String, ?> sourceNodes) {
            Map<String, HumanoidJoint> directOwners = directOwners(analysis);
            Map<String, Matrix4f> bindAbsolute = buildFullBindMatrices(snapshot, sourceNodes);
            Map<String, Matrix4f> desiredAbsolute = new HashMap<>();

            List<SourceNodeSnapshot> ordered = orderedNodes(snapshot);
            for (SourceNodeSnapshot node : ordered) {
                Object sourcePart = sourceNodes.get(node.id());
                Matrix4f bind = bindAbsolute.get(node.id());
                if (sourcePart == null || bind == null) {
                    continue;
                }

                HumanoidJoint owner = nearestOwner(snapshot, directOwners, node.id());
                Matrix4f targetAbsolute = new Matrix4f(skinMatrix(deformationJointId(owner))).mul(bind);
                Matrix4f sourceAbsolute = new Matrix4f(inverseExternalTransform).mul(targetAbsolute);
                desiredAbsolute.put(node.id(), sourceAbsolute);

                Matrix4f local = new Matrix4f(sourceAbsolute);
                if (node.parentId().isPresent()) {
                    Matrix4f parentAbsolute = desiredAbsolute.get(node.parentId().orElseThrow());
                    if (parentAbsolute != null) {
                        local = new Matrix4f(parentAbsolute).invert().mul(sourceAbsolute);
                    }
                }
                localOverrides.put(sourcePart, local);
            }
        }

        /** Rebuilds the same full bind matrices used by the framework adapters. */
        private Map<String, Matrix4f> buildFullBindMatrices(
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
            ResourceLocation entityId = EntityType.getKey(entity.getType());
            return new SkeletonKey(entityId, model.getClass().getName() + "#render_layer");
        }
    }

    private static List<SourceNodeSnapshot> orderedNodes(SourceRigSnapshot snapshot) {
        List<SourceNodeSnapshot> ordered = new ArrayList<>(snapshot.nodes().values());
        ordered.sort(Comparator.comparingInt(node -> snapshot.depth(node.id())));
        return ordered;
    }

    private static Map<String, HumanoidJoint> directOwners(HumanoidSemanticAnalysis analysis) {
        Map<String, HumanoidJoint> owners = new HashMap<>();
        for (SemanticJointCandidate candidate : analysis.candidates().values()) {
            if (candidate.origin() != JointOrigin.SOURCE) {
                continue;
            }
            candidate.sourceNodeId().ifPresent(sourceId -> owners.putIfAbsent(sourceId, candidate.joint()));
        }
        return owners;
    }

    private static HumanoidJoint nearestOwner(
            SourceRigSnapshot snapshot,
            Map<String, HumanoidJoint> directOwners,
            String sourceNodeId) {
        SourceNodeSnapshot cursor = snapshot.node(sourceNodeId);
        while (true) {
            HumanoidJoint owner = directOwners.get(cursor.id());
            if (owner != null) {
                return owner;
            }
            if (cursor.parentId().isEmpty()) {
                return null;
            }
            cursor = snapshot.node(cursor.parentId().orElseThrow());
        }
    }

    private static int deformationJointId(HumanoidJoint joint) {
        if (joint == null) {
            return HumanoidSkinWeightResolver.ROOT;
        }
        return switch (joint) {
            case ROOT, SPINE -> HumanoidSkinWeightResolver.ROOT;
            case CHEST -> HumanoidSkinWeightResolver.CHEST;
            case NECK, HEAD -> HumanoidSkinWeightResolver.HEAD;
            case SHOULDER_RIGHT -> HumanoidSkinWeightResolver.ARM_RIGHT;
            case ELBOW_RIGHT, WRIST_RIGHT -> HumanoidSkinWeightResolver.HAND_RIGHT;
            case SHOULDER_LEFT -> HumanoidSkinWeightResolver.ARM_LEFT;
            case ELBOW_LEFT, WRIST_LEFT -> HumanoidSkinWeightResolver.HAND_LEFT;
            case HIP_RIGHT -> HumanoidSkinWeightResolver.THIGH_RIGHT;
            case KNEE_RIGHT, ANKLE_RIGHT -> HumanoidSkinWeightResolver.LEG_RIGHT;
            case HIP_LEFT -> HumanoidSkinWeightResolver.THIGH_LEFT;
            case KNEE_LEFT, ANKLE_LEFT -> HumanoidSkinWeightResolver.LEG_LEFT;
        };
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

    private LayerPoseOverrideContext() {
    }
}
