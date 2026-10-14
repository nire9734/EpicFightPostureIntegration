package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.RenderUtils;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Narrow Gecko armor bridge for torso geometry that crosses the humanoid
 * Root/Chest deformation boundary.
 *
 * <p>The normal Gecko attachment bridge intentionally keeps armor, helmets,
 * sleeves and held items on the proven rigid attachment path. Only when a
 * {@link GeoArmorRenderer} is rendering a body-only subtree do we replace that
 * subtree with per-vertex Root/Chest skinning. This preserves the exact old
 * behaviour for head/arm/weapon attachments while allowing robe or coat geometry
 * extending below the waist to follow the lower body.</p>
 *
 * <p>No entity, item, renderer or mod id is inspected. The body subtree is found
 * from GeckoArmorRenderer's standard semantic bone accessors and is rejected
 * conservatively whenever other visible armor geometry would be swallowed by the
 * replacement.</p>
 */
public final class GeckoArmorBodySkinningContext {
    private static final float MATRIX_EPSILON = 1.0E-8F;
    private static final float ROOT_WEIGHT_EPSILON = 1.0E-5F;
    private static final int NON_TORSO_ANCHOR = Integer.MIN_VALUE;
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    public static FrameScope openFrame(
            LivingEntity entity,
            Armature armature,
            HumanoidSkeletonDescriptor skeleton,
            PoseStack basePoseStack) {
        Frame previous = ACTIVE.get();
        if (entity == null || armature == null || skeleton == null || basePoseStack == null) {
            return new FrameScope(previous, false);
        }

        Frame frame = new Frame(
                entity,
                armature,
                skeleton,
                new Matrix4f(basePoseStack.last().pose()),
                new Matrix3f(basePoseStack.last().normal())
        );
        ACTIVE.set(frame);
        return new FrameScope(previous, true);
    }

    /** Called from the ItemArmorGeoLayer mixin for the duration of one source bone callback. */
    public static void beginArmorBone(GeoBone sourceBone) {
        Frame frame = ACTIVE.get();
        if (frame == null) {
            return;
        }
        frame.anchorJoints.push(attachmentJointFor(sourceBone));
    }

    /** Balances {@link #beginArmorBone(GeoBone)} even when the layer returns early. */
    public static void endArmorBone() {
        Frame frame = ACTIVE.get();
        if (frame != null && !frame.anchorJoints.isEmpty()) {
            frame.anchorJoints.pop();
        }
    }

    /**
     * Renders and consumes the current GeoArmorRenderer subtree when it is a safe
     * body-only subtree that actually needs Root participation.
     *
     * @return true when the caller should cancel GeckoArmorRenderer's normal
     *         renderRecursively call for {@code currentBone}
     */
    public static boolean renderBodySubtreeIfActive(
            GeoArmorRenderer<?> renderer,
            BakedGeoModel bakedModel,
            GeoBone currentBone,
            PoseStack poseStack,
            VertexConsumer buffer,
            boolean isReRender,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha) {
        Frame frame = ACTIVE.get();
        if (frame == null
                || renderer == null
                || bakedModel == null
                || currentBone == null
                || poseStack == null
                || buffer == null
                || isReRender
                || frame.anchorJoints.isEmpty()
                || renderer.getCurrentEntity() != frame.entity) {
            return false;
        }

        if (renderer.getRenderLayers() != null && !renderer.getRenderLayers().isEmpty()) {
            return false;
        }

        int anchorJoint = frame.anchorJoints.peek();
        if (anchorJoint != HumanoidSkinWeightResolver.ROOT
                && anchorJoint != HumanoidSkinWeightResolver.CHEST) {
            return false;
        }

        GeoBone bodyBone = renderer.getBodyBone();
        if (bodyBone == null) {
            return false;
        }

        GeoBone bodyContainer = safeBodyContainer(renderer, bodyBone);
        if (bodyContainer == null || currentBone != bodyContainer) {
            return false;
        }

        if (hasTrackingBone(bodyContainer)) {
            return false;
        }

        if (!hasRenderableGeometry(bodyContainer)
                || hasRenderableGeometryOutside(bakedModel, bodyContainer)) {
            return false;
        }

        Matrix4f anchorSkin = frame.skinMatrix(anchorJoint);
        if (anchorSkin == null) {
            return false;
        }

        Matrix4f animatedAnchor = new Matrix4f(frame.basePose).mul(anchorSkin);
        float determinant = animatedAnchor.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) <= MATRIX_EPSILON) {
            return false;
        }

        // The incoming matrix is the old rigid attachment path. Remove only its
        // base * anchorSkin component so armor vertices return to the same model
        // space used by the humanoid descriptor and body mesh baker.
        Matrix4f localModelTransform = new Matrix4f(animatedAnchor)
                .invert()
                .mul(new Matrix4f(poseStack.last().pose()));
        Matrix3f localNormalTransform = normalMatrix(localModelTransform);
        if (localNormalTransform == null) {
            return false;
        }

        PoseStack localStack = new PoseStack();
        localStack.last().pose().set(localModelTransform);
        localStack.last().normal().set(localNormalTransform);

        List<VertexSample> samples = new ArrayList<>();
        RootUse rootUse = new RootUse();
        collectBodySubtree(bodyContainer, localStack, frame.skeleton, samples, rootUse);

        if (samples.isEmpty() || !rootUse.used) {
            return false;
        }

        for (VertexSample sample : samples) {
            Vector4f finalPosition = weightedPosition(frame, sample);
            Vector3f finalNormal = weightedNormal(frame, sample);
            Mesh.DrawingFunction.NEW_ENTITY.draw(
                    buffer,
                    finalPosition.x(),
                    finalPosition.y(),
                    finalPosition.z(),
                    finalNormal.x(),
                    finalNormal.y(),
                    finalNormal.z(),
                    packedLight,
                    red,
                    green,
                    blue,
                    alpha,
                    sample.u(),
                    sample.v(),
                    packedOverlay
            );
        }
        return true;
    }

    private static void collectBodySubtree(
            GeoBone bone,
            PoseStack stack,
            HumanoidSkeletonDescriptor skeleton,
            List<VertexSample> samples,
            RootUse rootUse) {
        if (bone == null) {
            return;
        }

        stack.pushPose();
        RenderUtils.prepMatrixForBone(stack, bone);

        if (!bone.isHidden() && bone.getCubes() != null) {
            for (GeoCube cube : bone.getCubes()) {
                if (cube == null || cube.quads() == null) {
                    continue;
                }

                stack.pushPose();
                RenderUtils.translateToPivotPoint(stack, cube);
                RenderUtils.rotateMatrixAroundCube(stack, cube);
                RenderUtils.translateAwayFromPivotPoint(stack, cube);

                for (GeoQuad quad : cube.quads()) {
                    if (quad == null || quad.normal() == null || quad.vertices() == null) {
                        continue;
                    }

                    Vector3f normal = new Vector3f(quad.normal()).mul(stack.last().normal());
                    if (normal.lengthSquared() > MATRIX_EPSILON) {
                        normal.normalize();
                    }

                    for (GeoVertex vertex : quad.vertices()) {
                        if (vertex == null || vertex.position() == null) {
                            continue;
                        }

                        Vector4f position = new Vector4f(vertex.position(), 1.0F)
                                .mul(stack.last().pose());
                        SkeletonVec3 modelPosition = new SkeletonVec3(
                                position.x(),
                                position.y(),
                                position.z()
                        );
                        HumanoidSkinWeightResolver.SkinWeights weights =
                                HumanoidSkinWeightResolver.torsoWeights(skeleton, modelPosition);

                        if (weightForJoint(weights, HumanoidSkinWeightResolver.ROOT)
                                > ROOT_WEIGHT_EPSILON) {
                            rootUse.used = true;
                        }

                        samples.add(new VertexSample(
                                new Vector3f(position.x(), position.y(), position.z()),
                                new Vector3f(normal),
                                vertex.texU(),
                                vertex.texV(),
                                weights
                        ));
                    }
                }

                stack.popPose();
            }
        }

        if (!bone.isHidingChildren() && bone.getChildBones() != null) {
            for (GeoBone child : bone.getChildBones()) {
                collectBodySubtree(child, stack, skeleton, samples, rootUse);
            }
        }

        stack.popPose();
    }

    private static GeoBone safeBodyContainer(GeoArmorRenderer<?> renderer, GeoBone bodyBone) {
        List<GeoBone> nonBodyBones = new ArrayList<>();
        addIfPresent(nonBodyBones, renderer.getHeadBone());
        addIfPresent(nonBodyBones, renderer.getRightArmBone());
        addIfPresent(nonBodyBones, renderer.getLeftArmBone());
        addIfPresent(nonBodyBones, renderer.getRightLegBone());
        addIfPresent(nonBodyBones, renderer.getLeftLegBone());
        addIfPresent(nonBodyBones, renderer.getRightBootBone());
        addIfPresent(nonBodyBones, renderer.getLeftBootBone());

        GeoBone candidate = bodyBone;
        GeoBone parent = parentOf(bodyBone);
        while (parent != null) {
            boolean containsNonBody = false;
            for (GeoBone other : nonBodyBones) {
                if (isDescendantOrSelf(other, parent)) {
                    containsNonBody = true;
                    break;
                }
            }
            if (containsNonBody) {
                break;
            }
            candidate = parent;
            parent = parentOf(parent);
        }
        return candidate;
    }

    private static void addIfPresent(List<GeoBone> bones, GeoBone bone) {
        if (bone != null) {
            bones.add(bone);
        }
    }

    private static boolean hasRenderableGeometryOutside(BakedGeoModel model, GeoBone container) {
        if (model.topLevelBones() == null) {
            return false;
        }
        for (GeoBone root : model.topLevelBones()) {
            if (hasRenderableGeometryOutside(root, container)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRenderableGeometryOutside(GeoBone bone, GeoBone container) {
        if (bone == null || bone == container) {
            return false;
        }
        if (!bone.isHidden() && hasCubes(bone)) {
            return true;
        }
        if (!bone.isHidingChildren() && bone.getChildBones() != null) {
            for (GeoBone child : bone.getChildBones()) {
                if (hasRenderableGeometryOutside(child, container)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasRenderableGeometry(GeoBone bone) {
        if (bone == null) {
            return false;
        }
        if (!bone.isHidden() && hasCubes(bone)) {
            return true;
        }
        if (!bone.isHidingChildren() && bone.getChildBones() != null) {
            for (GeoBone child : bone.getChildBones()) {
                if (hasRenderableGeometry(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCubes(GeoBone bone) {
        return bone.getCubes() != null && !bone.getCubes().isEmpty();
    }

    private static boolean hasTrackingBone(GeoBone bone) {
        if (bone == null) {
            return false;
        }
        if (bone.isTrackingMatrices()) {
            return true;
        }
        if (!bone.isHidingChildren() && bone.getChildBones() != null) {
            for (GeoBone child : bone.getChildBones()) {
                if (hasTrackingBone(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isDescendantOrSelf(GeoBone bone, GeoBone ancestor) {
        GeoBone cursor = bone;
        while (cursor != null) {
            if (cursor == ancestor) {
                return true;
            }
            cursor = parentOf(cursor);
        }
        return false;
    }

    private static GeoBone parentOf(GeoBone bone) {
        return bone == null ? null : bone.getParent();
    }

    /** Mirrors the established attachment bridge's semantic inheritance. */
    private static int attachmentJointFor(GeoBone bone) {
        if (bone == null) {
            return HumanoidSkinWeightResolver.ROOT;
        }

        List<GeoBone> chain = new ArrayList<>();
        GeoBone cursor = bone;
        while (cursor != null) {
            chain.add(0, cursor);
            cursor = parentOf(cursor);
        }

        int inherited = HumanoidSkinWeightResolver.ROOT;
        for (GeoBone current : chain) {
            Integer explicit = classifyAnchor(current, inherited);
            if (explicit != null) {
                inherited = explicit;
            }
        }
        return inherited;
    }

    private static Integer classifyAnchor(GeoBone bone, int inherited) {
        String name = normalize(bone.getName());
        if (name.isEmpty()) {
            return null;
        }

        if (isAny(name, "root", "master", "base", "main")) {
            return HumanoidSkinWeightResolver.ROOT;
        }
        if (name.equals("body")) {
            return hasTorsoChild(bone)
                    ? HumanoidSkinWeightResolver.ROOT
                    : HumanoidSkinWeightResolver.CHEST;
        }
        if (isAny(name, "torso", "chest", "upperbody", "trunk", "spine")) {
            return HumanoidSkinWeightResolver.CHEST;
        }
        if (containsAny(name, "head", "skull", "helmet", "hat")) {
            return NON_TORSO_ANCHOR;
        }
        if ((isRight(name) || isLeft(name))
                && containsAny(name,
                "hand", "palm", "wrist", "item", "weapon", "held",
                "elbow", "forearm", "lowerarm", "shoulder", "upperarm", "arm",
                "knee", "foot", "boot", "shin", "calf", "lowerleg", "thigh", "upperleg", "leg")) {
            return NON_TORSO_ANCHOR;
        }
        if (inherited == NON_TORSO_ANCHOR) {
            return NON_TORSO_ANCHOR;
        }
        if (containsAny(name, "cape", "cloak", "mantle", "back", "backpack")) {
            return HumanoidSkinWeightResolver.CHEST;
        }
        return null;
    }

    private static boolean hasTorsoChild(GeoBone bone) {
        if (bone.getChildBones() == null) {
            return false;
        }
        for (GeoBone child : bone.getChildBones()) {
            String name = normalize(child.getName());
            if (isAny(name, "torso", "chest", "upperbody", "trunk", "spine")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRight(String name) {
        return name.contains("right")
                || name.startsWith("rarm")
                || name.startsWith("rhand")
                || name.startsWith("rleg")
                || name.startsWith("rfoot")
                || name.endsWith("r");
    }

    private static boolean isLeft(String name) {
        return name.contains("left")
                || name.startsWith("larm")
                || name.startsWith("lhand")
                || name.startsWith("lleg")
                || name.startsWith("lfoot")
                || name.endsWith("l");
    }

    private static boolean isAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Matrix3f normalMatrix(Matrix4f matrix) {
        Matrix3f normal = new Matrix3f(matrix);
        float determinant = normal.determinant();
        if (!Float.isFinite(determinant) || Math.abs(determinant) <= MATRIX_EPSILON) {
            return null;
        }
        return normal.invert().transpose();
    }

    private static Vector4f weightedPosition(Frame frame, VertexSample sample) {
        HumanoidSkinWeightResolver.SkinWeights weights = sample.weights();
        Vector4f result = new Vector4f();
        addWeightedPosition(
                result,
                sample.position(),
                frame.skinMatrix(weights.firstJoint()),
                weights.firstWeight()
        );
        if (weights.count() > 1) {
            addWeightedPosition(
                    result,
                    sample.position(),
                    frame.skinMatrix(weights.secondJoint()),
                    weights.secondWeight()
            );
        }
        result.mul(frame.basePose);
        return result;
    }

    private static void addWeightedPosition(
            Vector4f destination,
            Vector3f source,
            Matrix4f skinMatrix,
            float weight) {
        if (skinMatrix == null || weight == 0.0F) {
            return;
        }
        destination.add(new Vector4f(source, 1.0F).mul(skinMatrix).mul(weight));
    }

    private static Vector3f weightedNormal(Frame frame, VertexSample sample) {
        HumanoidSkinWeightResolver.SkinWeights weights = sample.weights();
        Vector3f result = new Vector3f();
        addWeightedNormal(
                result,
                sample.normal(),
                frame.skinNormal(weights.firstJoint()),
                weights.firstWeight()
        );
        if (weights.count() > 1) {
            addWeightedNormal(
                    result,
                    sample.normal(),
                    frame.skinNormal(weights.secondJoint()),
                    weights.secondWeight()
            );
        }
        result.mul(frame.baseNormal);
        if (result.lengthSquared() > MATRIX_EPSILON) {
            result.normalize();
        }
        return result;
    }

    private static void addWeightedNormal(
            Vector3f destination,
            Vector3f source,
            Matrix3f skinNormal,
            float weight) {
        if (skinNormal == null || weight == 0.0F) {
            return;
        }
        destination.add(new Vector3f(source).mul(skinNormal).mul(weight));
    }

    private static float weightForJoint(
            HumanoidSkinWeightResolver.SkinWeights weights,
            int jointId) {
        float result = weights.firstJoint() == jointId ? weights.firstWeight() : 0.0F;
        if (weights.count() > 1 && weights.secondJoint() == jointId) {
            result += weights.secondWeight();
        }
        return result;
    }

    public static final class FrameScope implements AutoCloseable {
        private final Frame previous;
        private final boolean installed;
        private boolean closed;

        private FrameScope(Frame previous, boolean installed) {
            this.previous = previous;
            this.installed = installed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (!installed) {
                return;
            }
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    private static final class Frame {
        private final LivingEntity entity;
        private final Armature armature;
        private final HumanoidSkeletonDescriptor skeleton;
        private final Matrix4f basePose;
        private final Matrix3f baseNormal;
        private final ArrayDeque<Integer> anchorJoints = new ArrayDeque<>();
        private final Map<Integer, Matrix4f> skinMatrices = new HashMap<>();
        private final Map<Integer, Matrix3f> skinNormals = new HashMap<>();

        private Frame(
                LivingEntity entity,
                Armature armature,
                HumanoidSkeletonDescriptor skeleton,
                Matrix4f basePose,
                Matrix3f baseNormal) {
            this.entity = entity;
            this.armature = armature;
            this.skeleton = skeleton;
            this.basePose = basePose;
            this.baseNormal = baseNormal;
        }

        private Matrix4f skinMatrix(int jointId) {
            Matrix4f cached = skinMatrices.get(jointId);
            if (cached != null) {
                return cached;
            }

            OpenMatrix4f[] poseMatrices = armature.getPoseMatrices();
            if (poseMatrices == null
                    || jointId < 0
                    || jointId >= poseMatrices.length
                    || poseMatrices[jointId] == null) {
                return null;
            }

            var joint = armature.searchJointById(jointId);
            if (joint == null) {
                return null;
            }

            OpenMatrix4f skin = new OpenMatrix4f(poseMatrices[jointId]);
            skin.mulBack(joint.getToOrigin());
            Matrix4f result = OpenMatrix4f.exportToMojangMatrix(skin);
            skinMatrices.put(jointId, result);
            return result;
        }

        private Matrix3f skinNormal(int jointId) {
            Matrix3f cached = skinNormals.get(jointId);
            if (cached != null) {
                return cached;
            }
            Matrix4f skin = skinMatrix(jointId);
            if (skin == null) {
                return null;
            }
            Matrix3f result = new Matrix3f(skin);
            skinNormals.put(jointId, result);
            return result;
        }
    }

    private record VertexSample(
            Vector3f position,
            Vector3f normal,
            float u,
            float v,
            HumanoidSkinWeightResolver.SkinWeights weights) {
    }

    private static final class RootUse {
        private boolean used;
    }

    private GeckoArmorBodySkinningContext() {
    }
}
