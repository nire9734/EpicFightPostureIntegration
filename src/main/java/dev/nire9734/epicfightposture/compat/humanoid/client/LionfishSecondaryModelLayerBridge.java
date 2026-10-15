package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Replaces safely analyzable layer-owned Lionfish clothing/overlay models with a
 * cached skinned mesh that uses the primary humanoid descriptor.
 *
 * <p>The layer still owns texture, RenderType, light, overlay, color and visibility.
 * EFPI substitutes geometry only when the secondary model contains a canonical body
 * transition that cannot be represented by one rigid joint. Rigid-only or ambiguous
 * models fall through to LayerPoseOverrideContext.</p>
 *
 * <p>Like the primary Lionfish layer bridge, this path requires the source
 * RenderType to be captured. SkinnedMesh is triangulated and must be rendered via
 * SkinnedMesh.draw() so Epic Fight can convert a source QUADS RenderType into an
 * equivalent TRIANGLES RenderType without changing its shader/texture/state.</p>
 */
public final class LionfishSecondaryModelLayerBridge {
    private static final float MATRIX_EPSILON = 1.0E-8F;
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    /** Model identity changes naturally invalidate resource-reloaded models. */
    private static final Map<AdvancedEntityModel<?>, Map<CacheKey, CachedSecondary>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    static Scope open(
            Object primaryModel,
            HumanoidSkeletonDescriptor primaryDescriptor,
            Armature armature) {
        Frame previous = ACTIVE.get();
        if (primaryModel == null || primaryDescriptor == null || armature == null) {
            return new Scope(previous, false);
        }

        OpenMatrix4f[] poses = armature.getPoseMatrices();
        int rootId = armature.rootJoint.getId();
        if (poses == null || rootId < 0 || rootId >= poses.length || poses[rootId] == null) {
            return new Scope(previous, false);
        }

        Matrix4f external = OpenMatrix4f.exportToMojangMatrix(poses[rootId]);
        external.scale(-1.0F, -1.0F, 1.0F);
        Matrix4f inverseExternal = new Matrix4f(external).invert();

        ACTIVE.set(new Frame(
                primaryModel,
                primaryDescriptor,
                descriptorFingerprint(primaryDescriptor),
                armature,
                poses,
                inverseExternal
        ));
        return new Scope(previous, true);
    }

    /** Called by the optional Lionfish BasicEntityModel renderToBuffer mixin. */
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
        if (frame == null || poseStack == null || vertexConsumer == null) {
            return false;
        }
        if (renderedModel == frame.primaryModel) {
            LionfishRetargetDiagnostics.secondaryBridge(
                    renderedModel, "SKIP", "primary_model", poseStack);
            return false;
        }
        if (!(renderedModel instanceof AdvancedEntityModel<?> secondaryModel)) {
            LionfishRetargetDiagnostics.secondaryBridge(
                    renderedModel, "SKIP", "not_advanced_entity_model", poseStack);
            return false;
        }

        LionfishLayerRenderTargetCapture.Target target =
                LionfishLayerRenderTargetCapture.resolve(vertexConsumer);
        if (target == null) {
            LionfishRetargetDiagnostics.secondaryBridge(
                    renderedModel, "FALLBACK", "render_type_not_captured", poseStack);
            return false;
        }

        VisibilityKey visibility = visibilityKey(secondaryModel);
        if (visibility == null) {
            LionfishRetargetDiagnostics.secondaryBridge(
                    renderedModel, "FALLBACK", "visibility_unavailable", poseStack);
            return false;
        }

        CacheKey cacheKey = new CacheKey(
                frame.primaryDescriptor.key(),
                frame.descriptorFingerprint,
                visibility
        );
        CachedSecondary cached = cachedSecondary(secondaryModel, cacheKey);
        if (cached == null) {
            cached = bakeSecondary(secondaryModel, cacheKey, frame.primaryDescriptor);
            cacheSecondary(secondaryModel, cacheKey, cached);
        }
        if (!shouldReplace(cached)) {
            LionfishRetargetDiagnostics.secondaryBridge(
                    renderedModel, "FALLBACK", "secondary_not_safely_deformable", poseStack);
            return false;
        }

        poseStack.pushPose();
        try {
            mulPoseAndNormal(poseStack, frame.inverseExternalTransform);
            LionfishRetargetDiagnostics.secondaryBridge(
                    renderedModel,
                    "REPLACE",
                    "renderType=" + target.renderType() + ",mode=" + target.renderType().mode(),
                    poseStack
            );
            cached.mesh().draw(
                    poseStack,
                    target.bufferSource(),
                    target.renderType(),
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

    static boolean shouldReplace(CachedSecondary cached) {
        return cached != null && cached.supported() && cached.mesh() != null;
    }

    private static CachedSecondary bakeSecondary(
            AdvancedEntityModel<?> model,
            CacheKey cacheKey,
            HumanoidSkeletonDescriptor primaryDescriptor) {
        SkeletonKey secondaryKey = new SkeletonKey(
                cacheKey.primaryKey().entityTypeId(),
                cacheKey.primaryKey().modelSignature()
                        + "|secondary|" + model.getClass().getName()
        );
        LionfishHumanoidMeshAdapter.SecondaryResult result =
                LionfishHumanoidMeshAdapter.analyseAndBakeSecondary(
                        model,
                        secondaryKey,
                        primaryDescriptor
                );
        return result.supported() && result.mesh() != null
                ? CachedSecondary.supported(result.mesh())
                : CachedSecondary.unsupported();
    }

    private static CachedSecondary cachedSecondary(AdvancedEntityModel<?> model, CacheKey key) {
        synchronized (CACHE) {
            Map<CacheKey, CachedSecondary> byKey = CACHE.get(model);
            return byKey == null ? null : byKey.get(key);
        }
    }

    private static void cacheSecondary(
            AdvancedEntityModel<?> model,
            CacheKey key,
            CachedSecondary cached) {
        synchronized (CACHE) {
            CACHE.computeIfAbsent(model, ignored -> new HashMap<>()).put(key, cached);
        }
    }

    private static void mulPoseAndNormal(PoseStack poseStack, Matrix4f transform) {
        poseStack.mulPoseMatrix(new Matrix4f(transform));
        Matrix3f normalTransform = new Matrix3f(transform);
        float determinant = normalTransform.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > MATRIX_EPSILON) {
            normalTransform.invert().transpose();
            poseStack.last().normal().mul(normalTransform);
        }
    }

    private static VisibilityKey visibilityKey(AdvancedEntityModel<?> model) {
        try {
            List<PartVisibility> parts = new ArrayList<>();
            for (AdvancedModelBox part : model.getAllParts()) {
                if (part != null) {
                    parts.add(new PartVisibility(part.showModel, part.isHidden()));
                }
            }
            return parts.isEmpty() ? null : new VisibilityKey(List.copyOf(parts));
        } catch (Throwable ignored) {
            return null;
        }
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

    static record CachedSecondary(boolean supported, SkinnedMesh mesh) {
        static CachedSecondary supported(SkinnedMesh mesh) {
            return new CachedSecondary(true, mesh);
        }

        static CachedSecondary unsupported() {
            return new CachedSecondary(false, null);
        }
    }

    private record Frame(
            Object primaryModel,
            HumanoidSkeletonDescriptor primaryDescriptor,
            int descriptorFingerprint,
            Armature armature,
            OpenMatrix4f[] poseMatrices,
            Matrix4f inverseExternalTransform) {
    }

    private record CacheKey(
            SkeletonKey primaryKey,
            int descriptorFingerprint,
            VisibilityKey visibility) {
    }

    private record VisibilityKey(List<PartVisibility> parts) {
    }

    private record PartVisibility(boolean showModel, boolean hidden) {
    }

    private LionfishSecondaryModelLayerBridge() {
    }
}
