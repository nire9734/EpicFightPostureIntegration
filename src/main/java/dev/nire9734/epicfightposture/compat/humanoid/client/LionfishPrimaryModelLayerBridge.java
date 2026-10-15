package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reuses the already-baked Epic Fight body mesh when a Lionfish RenderLayer
 * redraws the exact same parent model.
 *
 * <p>The ordinary Lionfish layer fallback works per source part and is necessarily
 * rigid. A one-bone source limb, however, is skinned per vertex across the inferred
 * elbow/knee in the prepared body mesh. Redrawing the same source model rigidly can
 * therefore separate long limb decorations from the body. Reusing the primary mesh
 * preserves the exact body weights while still honoring the layer's VertexConsumer,
 * light, overlay and color contract.</p>
 *
 * <p>Only the exact primary model instance is substituted. Layers that own another
 * model, held-item paths, or layers that alter primary-part visibility continue
 * through the existing source-model replay path.</p>
 */
public final class LionfishPrimaryModelLayerBridge {
    private static final float MATRIX_EPSILON = 1.0E-8F;
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    static Scope open(Object primaryModel, SkinnedMesh primaryMesh, Armature armature) {
        Frame previous = ACTIVE.get();
        if (primaryModel == null || primaryMesh == null || armature == null) {
            return new Scope(previous, false);
        }

        OpenMatrix4f[] poses = armature.getPoseMatrices();
        int rootId = armature.rootJoint.getId();
        if (poses == null || rootId < 0 || rootId >= poses.length || poses[rootId] == null) {
            return new Scope(previous, false);
        }

        VisibilitySnapshot visibility = captureVisibility(primaryModel);
        if (visibility == null) {
            return new Scope(previous, false);
        }

        Matrix4f external = OpenMatrix4f.exportToMojangMatrix(poses[rootId]);
        external.scale(-1.0F, -1.0F, 1.0F);
        Matrix4f inverseExternal = new Matrix4f(external).invert();

        ACTIVE.set(new Frame(
                primaryModel,
                primaryMesh,
                armature,
                poses,
                inverseExternal,
                visibility
        ));
        return new Scope(previous, true);
    }

    /**
     * Called by the optional Lionfish BasicEntityModel mixin at renderToBuffer HEAD.
     * Returning true means the source model render should be cancelled.
     */
    public static boolean renderIfPresent(
            Object renderedModel,
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha) {
        Frame frame = ACTIVE.get();
        if (frame == null
                || renderedModel != frame.primaryModel
                || poseStack == null
                || vertexConsumer == null
                || !visibilityMatches(frame.visibility)) {
            return false;
        }

        poseStack.pushPose();
        try {
            // LivingRenderLayerBridge enters source layers underneath
            // RootPose * source-axis-flip. The prepared SkinnedMesh already applies
            // the complete EF skin matrices, so remove only that replay basis and
            // preserve the renderer-owned/model presentation transform beneath it.
            mulPoseAndNormal(poseStack, frame.inverseExternalTransform);
            frame.primaryMesh.drawPosed(
                    poseStack,
                    vertexConsumer,
                    Mesh.DrawingFunction.NEW_ENTITY,
                    packedLight,
                    red,
                    green,
                    blue,
                    alpha,
                    packedOverlay,
                    frame.armature,
                    frame.poseMatrices
            );
        } finally {
            poseStack.popPose();
        }
        return true;
    }

    private static void mulPoseAndNormal(PoseStack poseStack, Matrix4f transform) {
        poseStack.mulPoseMatrix(new Matrix4f(transform));

        // Minecraft 1.20.1 PoseStack.mulPoseMatrix updates only the position
        // matrix. Keep normals equivalent to multiplying the full inverse basis.
        Matrix3f normalTransform = new Matrix3f(transform);
        float determinant = normalTransform.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > MATRIX_EPSILON) {
            normalTransform.invert().transpose();
            poseStack.last().normal().mul(normalTransform);
        }
    }

    static VisibilitySnapshot captureVisibility(Object model) {
        try {
            Method partsMethod = findMethod(model.getClass(), "parts");
            if (partsMethod == null) {
                return null;
            }
            if (!partsMethod.canAccess(model)) {
                partsMethod.setAccessible(true);
            }
            Object rootsValue = partsMethod.invoke(model);
            if (!(rootsValue instanceof Iterable<?> roots)) {
                return null;
            }

            IdentityHashMap<Object, PartVisibility> parts = new IdentityHashMap<>();
            for (Object root : roots) {
                capturePartVisibility(root, parts);
            }
            return parts.isEmpty() ? null : new VisibilitySnapshot(parts);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean visibilityMatches(VisibilitySnapshot expected) {
        if (expected == null) {
            return false;
        }
        for (Map.Entry<Object, PartVisibility> entry : expected.parts.entrySet()) {
            PartVisibility current = readPartVisibility(entry.getKey());
            if (current == null || !current.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static void capturePartVisibility(
            Object part,
            IdentityHashMap<Object, PartVisibility> output) throws ReflectiveOperationException {
        if (part == null || output.containsKey(part)) {
            return;
        }

        PartVisibility visibility = readPartVisibility(part);
        if (visibility == null) {
            throw new ReflectiveOperationException("Could not read Lionfish part visibility");
        }
        output.put(part, visibility);

        Field childrenField = findField(part.getClass(), "childModels");
        if (childrenField == null) {
            return;
        }
        if (!childrenField.canAccess(part)) {
            childrenField.setAccessible(true);
        }
        Object childrenValue = childrenField.get(part);
        if (childrenValue instanceof Iterable<?> children) {
            for (Object child : children) {
                capturePartVisibility(child, output);
            }
        }
    }

    private static PartVisibility readPartVisibility(Object part) {
        if (part == null) {
            return null;
        }
        try {
            Field showModelField = findField(part.getClass(), "showModel");
            if (showModelField == null) {
                return null;
            }
            if (!showModelField.canAccess(part)) {
                showModelField.setAccessible(true);
            }
            boolean showModel = showModelField.getBoolean(part);

            Method hiddenMethod = findMethod(part.getClass(), "isHidden");
            boolean hidden = false;
            if (hiddenMethod != null) {
                if (!hiddenMethod.canAccess(part)) {
                    hiddenMethod.setAccessible(true);
                }
                Object hiddenValue = hiddenMethod.invoke(part);
                if (hiddenValue instanceof Boolean value) {
                    hidden = value;
                }
            }
            return new PartVisibility(showModel, hidden);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    return method;
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    static final class VisibilitySnapshot {
        private final IdentityHashMap<Object, PartVisibility> parts;

        private VisibilitySnapshot(IdentityHashMap<Object, PartVisibility> parts) {
            this.parts = new IdentityHashMap<>(parts);
        }
    }

    private record PartVisibility(boolean showModel, boolean hidden) {
    }

    static final class Scope implements AutoCloseable {
        private final Frame previous;
        private final boolean installed;
        private boolean closed;

        private Scope(Frame previous, boolean installed) {
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

    private record Frame(
            Object primaryModel,
            SkinnedMesh primaryMesh,
            Armature armature,
            OpenMatrix4f[] poseMatrices,
            Matrix4f inverseExternalTransform,
            VisibilitySnapshot visibility) {
    }

    private LionfishPrimaryModelLayerBridge() {
    }
}
