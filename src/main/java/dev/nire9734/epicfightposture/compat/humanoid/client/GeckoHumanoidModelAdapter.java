package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.util.RenderUtils;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.MeshPartDefinition;
import yesman.epicfight.api.client.model.SingleGroupVertexBuilder;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.GeoModelTransformer;
import yesman.epicfight.api.client.model.transformer.HumanoidModelTransformer;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec2f;
import yesman.epicfight.api.utils.math.Vec3f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Generic GeckoLib humanoid analyser + mesh bridge.
 *
 * <p>Humanoid detection is independent from mesh conversion, so a renderer
 * experiment cannot remove posture/execution compatibility. The normal mesh
 * path stays deliberately conservative. Only a limb that has no explicit
 * forearm/lower-leg bone is delegated to Epic Fight 20.14.x's own
 * GeoModelTransformer LimbPartTransformer. That transformer clips the cube at
 * the BIPED elbow/knee plane and creates the exact Arm/Hand/Elbow or
 * Thigh/Leg/Knee joint assignment used by Epic Fight.</p>
 */
public final class GeckoHumanoidModelAdapter {
    private static final MeshPartDefinition PART = new RuntimeGeoPart("gecko_humanoid");

    private static final int ROOT = 0;
    private static final int THIGH_R = 1;
    private static final int LEG_R = 2;
    private static final int THIGH_L = 4;
    private static final int LEG_L = 5;
    private static final int CHEST = 8;
    private static final int HEAD = 9;
    private static final int ARM_R = 11;
    private static final int HAND_R = 12;
    private static final int ARM_L = 16;
    private static final int HAND_L = 17;

    private static final Set<String> BODY = aliases(
            "body", "torso", "chest", "upperbody", "upper_body", "trunk");
    private static final Set<String> HEAD_NAMES = aliases("head", "skull");
    private static final Set<String> RIGHT_ARM = aliases(
            "rightarm", "right_arm", "armright", "arm_right", "rarm", "armr",
            "rightupperarm", "upperarmright", "upper_arm_right");
    private static final Set<String> LEFT_ARM = aliases(
            "leftarm", "left_arm", "armleft", "arm_left", "larm", "arml",
            "leftupperarm", "upperarmleft", "upper_arm_left");
    private static final Set<String> RIGHT_FOREARM = aliases(
            "rightforearm", "right_forearm", "forearmright", "forearm_right",
            "rightlowerarm", "right_lower_arm", "lowerarmright", "lower_arm_right",
            "rforearm");
    private static final Set<String> LEFT_FOREARM = aliases(
            "leftforearm", "left_forearm", "forearmleft", "forearm_left",
            "leftlowerarm", "left_lower_arm", "lowerarmleft", "lower_arm_left",
            "lforearm");
    private static final Set<String> RIGHT_LEG = aliases(
            "rightleg", "right_leg", "legright", "leg_right", "rleg",
            "rightthigh", "right_thigh", "thighright", "thigh_right");
    private static final Set<String> LEFT_LEG = aliases(
            "leftleg", "left_leg", "legleft", "leg_left", "lleg",
            "leftthigh", "left_thigh", "thighleft", "thigh_left");
    private static final Set<String> RIGHT_LOWER_LEG = aliases(
            "rightlowerleg", "right_lower_leg", "lowerlegright", "lower_leg_right",
            "rightshin", "right_shin", "shinright", "rshin", "rightcalf");
    private static final Set<String> LEFT_LOWER_LEG = aliases(
            "leftlowerleg", "left_lower_leg", "lowerlegleft", "lower_leg_left",
            "leftshin", "left_shin", "shinleft", "lshin", "leftcalf");
    private static final Set<String> ROOT_NAMES = aliases("root", "master", "base", "main");

    private static volatile LimbTransformers cachedLimbTransformers;

    /** Pure semantic probe. No mesh generation is performed here. */
    public static ProbeResult probe(BakedGeoModel model) {
        Resolution resolution = resolve(model);
        return resolution == null
                ? ProbeResult.unsupported("essential humanoid bones were missing or ambiguous")
                : new ProbeResult(true, "ok");
    }

    public static Result analyseAndBake(BakedGeoModel model) {
        Resolution resolution = resolve(model);
        if (resolution == null) {
            return Result.unsupported("essential humanoid bones were missing or ambiguous");
        }

        try {
            for (GeoBone top : model.topLevelBones()) {
                resetToInitial(top);
            }

            Map<GeoBone, Integer> joints = new HashMap<>();
            GeoBone root = findBest(resolution.bones(), ROOT_NAMES);
            if (root == null) {
                root = topAncestor(resolution.chest());
            }

            if (root != null) joints.put(root, ROOT);
            joints.put(resolution.chest(), CHEST);
            joints.put(resolution.head(), HEAD);
            joints.put(resolution.rightArm(), ARM_R);
            joints.put(resolution.leftArm(), ARM_L);
            joints.put(resolution.rightLeg(), THIGH_R);
            joints.put(resolution.leftLeg(), THIGH_L);

            GeoBone rightForearm = findDescendantBest(resolution.rightArm(), RIGHT_FOREARM);
            GeoBone leftForearm = findDescendantBest(resolution.leftArm(), LEFT_FOREARM);
            GeoBone rightLowerLeg = findDescendantBest(resolution.rightLeg(), RIGHT_LOWER_LEG);
            GeoBone leftLowerLeg = findDescendantBest(resolution.leftLeg(), LEFT_LOWER_LEG);

            if (rightForearm != null) joints.put(rightForearm, HAND_R);
            if (leftForearm != null) joints.put(leftForearm, HAND_L);
            if (rightLowerLeg != null) joints.put(rightLowerLeg, LEG_R);
            if (leftLowerLeg != null) joints.put(leftLowerLeg, LEG_L);

            // Only one-bone limbs need synthetic elbow/knee geometry. Rigs with
            // an explicit lower limb already have a real hinge and stay on the
            // conservative mapping above.
            Map<GeoBone, HumanoidModelTransformer.PartTransformer<GeoCube>> splitters = new HashMap<>();
            LimbTransformers ef = limbTransformers();
            if (rightForearm == null) splitters.put(resolution.rightArm(), ef.rightArm());
            if (leftForearm == null) splitters.put(resolution.leftArm(), ef.leftArm());
            if (rightLowerLeg == null) splitters.put(resolution.rightLeg(), ef.rightLeg());
            if (leftLowerLeg == null) splitters.put(resolution.leftLeg(), ef.leftLeg());

            SkinnedMesh mesh = bake(model, joints, splitters);
            return mesh == null
                    ? Result.unsupported("mesh bake returned null")
                    : Result.supported(mesh);
        } catch (Throwable throwable) {
            return Result.unsupported("mesh bake failed: " + throwable.getClass().getSimpleName());
        }
    }

    private static Resolution resolve(BakedGeoModel model) {
        if (model == null || model.topLevelBones() == null || model.topLevelBones().isEmpty()) {
            return null;
        }

        List<BoneCandidate> bones = new ArrayList<>();
        for (GeoBone root : model.topLevelBones()) {
            collect(root, 0, bones);
        }

        GeoBone chest = findBestBody(bones);
        GeoBone bodyAnchor = findBest(bones, BODY);
        GeoBone head = findBest(bones, HEAD_NAMES);
        GeoBone rightArm = findBest(bones, RIGHT_ARM);
        GeoBone leftArm = findBest(bones, LEFT_ARM);
        GeoBone rightLeg = findBest(bones, RIGHT_LEG);
        GeoBone leftLeg = findBest(bones, LEFT_LEG);

        if (!allDistinct(chest, head, rightArm, leftArm, rightLeg, leftLeg)) {
            return null;
        }

        GeoBone hierarchyAnchor = bodyAnchor != null ? bodyAnchor : chest;
        if (!isRelated(hierarchyAnchor, head)
                || !isRelated(hierarchyAnchor, rightArm)
                || !isRelated(hierarchyAnchor, leftArm)
                || !isRelated(hierarchyAnchor, rightLeg)
                || !isRelated(hierarchyAnchor, leftLeg)) {
            return null;
        }

        return new Resolution(
                List.copyOf(bones),
                chest,
                head,
                rightArm,
                leftArm,
                rightLeg,
                leftLeg);
    }

    private static SkinnedMesh bake(
            BakedGeoModel model,
            Map<GeoBone, Integer> joints,
            Map<GeoBone, HumanoidModelTransformer.PartTransformer<GeoCube>> splitters) {
        List<SingleGroupVertexBuilder> vertices = new ArrayList<>();
        Map<MeshPartDefinition, IntList> indices = new HashMap<>();
        PoseStack stack = new PoseStack();

        for (GeoBone top : model.topLevelBones()) {
            bakeBone(top, ROOT, null, joints, splitters, stack, vertices, indices);
        }

        if (vertices.isEmpty()) {
            return null;
        }
        return SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
    }

    private static void bakeBone(
            GeoBone bone,
            int inheritedJoint,
            HumanoidModelTransformer.PartTransformer<GeoCube> inheritedSplitter,
            Map<GeoBone, Integer> joints,
            Map<GeoBone, HumanoidModelTransformer.PartTransformer<GeoCube>> splitters,
            PoseStack stack,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) {
        if (bone == null || bone.isHidden()) {
            return;
        }

        int joint = joints.getOrDefault(bone, inheritedJoint);
        HumanoidModelTransformer.PartTransformer<GeoCube> activeSplitter =
                splitters.getOrDefault(bone, inheritedSplitter);

        stack.pushPose();
        RenderUtils.prepMatrixForBone(stack, bone);

        for (GeoCube cube : bone.getCubes()) {
            if (cube == null || cube.quads() == null) {
                continue;
            }

            stack.pushPose();
            RenderUtils.translateToPivotPoint(stack, cube);
            RenderUtils.rotateMatrixAroundCube(stack, cube);
            RenderUtils.translateAwayFromPivotPoint(stack, cube);

            if (activeSplitter != null) {
                bakeSplitCube(activeSplitter, stack, cube, vertices, indices);
            } else {
                bakeSimpleCube(joint, stack, cube, vertices, indices);
            }

            stack.popPose();
        }

        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                bakeBone(
                        child,
                        joint,
                        activeSplitter,
                        joints,
                        splitters,
                        stack,
                        vertices,
                        indices);
            }
        }
        stack.popPose();
    }

    /**
     * Runs one cube through Epic Fight's own LimbPartTransformer in an isolated
     * local vertex/index buffer, then rebases its indices into the already-built
     * conservative mesh. This avoids depending on IndexCounter's private state.
     */
    private static void bakeSplitCube(
            HumanoidModelTransformer.PartTransformer<GeoCube> splitter,
            PoseStack stack,
            GeoCube cube,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) {
        List<SingleGroupVertexBuilder> localVertices = new ArrayList<>();
        Map<MeshPartDefinition, IntList> localIndices = new HashMap<>();
        HumanoidModelTransformer.PartTransformer.IndexCounter localCounter =
                new HumanoidModelTransformer.PartTransformer.IndexCounter();

        splitter.bakeCube(stack, PART, cube, localVertices, localIndices, localCounter);
        if (localVertices.isEmpty()) {
            return;
        }

        int vertexOffset = vertices.size();
        vertices.addAll(localVertices);

        for (Map.Entry<MeshPartDefinition, IntList> entry : localIndices.entrySet()) {
            IntList target = indices.computeIfAbsent(entry.getKey(), ignored -> new IntArrayList());
            IntList source = entry.getValue();
            for (int i = 0; i < source.size(); i++) {
                target.add(source.getInt(i) + vertexOffset);
            }
        }
    }

    private static void bakeSimpleCube(
            int joint,
            PoseStack stack,
            GeoCube cube,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) {
        for (GeoQuad quad : cube.quads()) {
            if (quad == null || quad.vertices() == null || quad.vertices().length != 4) {
                continue;
            }

            Vector3f normal = new Vector3f(quad.normal());
            normal.mul(stack.last().normal());

            int first = vertices.size();
            for (GeoVertex vertex : quad.vertices()) {
                Vector4f pos = new Vector4f(vertex.position(), 1.0F);
                pos.mul(stack.last().pose());
                vertices.add(new SingleGroupVertexBuilder()
                        .setPosition(new Vec3f(pos.x(), pos.y(), pos.z()))
                        .setNormal(new Vec3f(normal.x(), normal.y(), normal.z()))
                        .setTextureCoordinate(new Vec2f(vertex.texU(), vertex.texV()))
                        .setEffectiveJointIDs(new Vec3f(joint, 0, 0))
                        .setEffectiveJointWeights(new Vec3f(1.0F, 0.0F, 0.0F))
                        .setEffectiveJointNumber(1));
            }

            triangulateQuad(indices, PART, first);
        }
    }

    private static void triangulateQuad(
            Map<MeshPartDefinition, IntList> indices,
            MeshPartDefinition part,
            int first) {
        IntList list = indices.computeIfAbsent(part, key -> new IntArrayList());
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

    private static LimbTransformers limbTransformers() throws ReflectiveOperationException {
        LimbTransformers cached = cachedLimbTransformers;
        if (cached != null) {
            return cached;
        }

        synchronized (GeckoHumanoidModelAdapter.class) {
            cached = cachedLimbTransformers;
            if (cached != null) {
                return cached;
            }

            cached = new LimbTransformers(
                    transformerField("RIGHT_ARM"),
                    transformerField("LEFT_ARM"),
                    transformerField("RIGHT_LEG"),
                    transformerField("LEFT_LEG"));
            cachedLimbTransformers = cached;
            return cached;
        }
    }

    @SuppressWarnings("unchecked")
    private static HumanoidModelTransformer.PartTransformer<GeoCube> transformerField(String name)
            throws ReflectiveOperationException {
        Field field = GeoModelTransformer.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(null);
        if (!(value instanceof HumanoidModelTransformer.PartTransformer<?> transformer)) {
            throw new ReflectiveOperationException("Unexpected Epic Fight transformer field: " + name);
        }
        return (HumanoidModelTransformer.PartTransformer<GeoCube>) transformer;
    }

    private static void resetToInitial(GeoBone bone) {
        if (bone == null) return;
        BoneSnapshot snapshot = bone.getInitialSnapshot();
        if (snapshot != null) {
            bone.setPosX(snapshot.getOffsetX());
            bone.setPosY(snapshot.getOffsetY());
            bone.setPosZ(snapshot.getOffsetZ());
            bone.setRotX(snapshot.getRotX());
            bone.setRotY(snapshot.getRotY());
            bone.setRotZ(snapshot.getRotZ());
            bone.setScaleX(snapshot.getScaleX());
            bone.setScaleY(snapshot.getScaleY());
            bone.setScaleZ(snapshot.getScaleZ());
        }
        for (GeoBone child : bone.getChildBones()) {
            resetToInitial(child);
        }
    }

    private static void collect(GeoBone bone, int depth, List<BoneCandidate> out) {
        if (bone == null) return;
        out.add(new BoneCandidate(bone, depth, normalize(bone.getName())));
        for (GeoBone child : bone.getChildBones()) {
            collect(child, depth + 1, out);
        }
    }

    private static GeoBone findBestBody(List<BoneCandidate> bones) {
        BoneCandidate best = null;
        int bestScore = 0;
        boolean tied = false;

        for (BoneCandidate candidate : bones) {
            int score = score(candidate.normalizedName(), BODY) - candidate.depth();
            if (candidate.bone().getCubes() != null && !candidate.bone().getCubes().isEmpty()) {
                score += 20;
            }

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                tied = false;
            } else if (score > 0 && score == bestScore) {
                tied = true;
            }
        }

        return bestScore >= 70 && !tied && best != null ? best.bone() : null;
    }

    private static GeoBone findBest(List<BoneCandidate> bones, Set<String> aliases) {
        BoneCandidate best = null;
        int bestScore = 0;
        boolean tied = false;
        for (BoneCandidate candidate : bones) {
            int score = score(candidate.normalizedName(), aliases) - candidate.depth();
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                tied = false;
            } else if (score > 0 && score == bestScore) {
                tied = true;
            }
        }
        return bestScore >= 70 && !tied && best != null ? best.bone() : null;
    }

    private static GeoBone findDescendantBest(GeoBone parent, Set<String> aliases) {
        if (parent == null) return null;
        List<BoneCandidate> descendants = new ArrayList<>();
        for (GeoBone child : parent.getChildBones()) {
            collect(child, 1, descendants);
        }
        return findBest(descendants, aliases);
    }

    private static int score(String name, Set<String> aliases) {
        if (name == null || name.isEmpty()) return 0;
        int best = 0;
        for (String alias : aliases) {
            if (name.equals(alias)) best = Math.max(best, 100);
            else if (name.startsWith(alias) || name.endsWith(alias)) best = Math.max(best, 82);
        }
        return best;
    }

    private static boolean allDistinct(GeoBone... bones) {
        Set<GeoBone> set = new HashSet<>();
        for (GeoBone bone : bones) {
            if (bone == null || !set.add(bone)) return false;
        }
        return true;
    }

    private static boolean isRelated(GeoBone ancestorCandidate, GeoBone childCandidate) {
        GeoBone cursor = childCandidate;
        while (cursor != null) {
            if (cursor == ancestorCandidate) return true;
            cursor = cursor.getParent();
        }
        return topAncestor(ancestorCandidate) == topAncestor(childCandidate);
    }

    private static GeoBone topAncestor(GeoBone bone) {
        GeoBone cursor = bone;
        while (cursor != null && cursor.getParent() != null) {
            cursor = cursor.getParent();
        }
        return cursor;
    }

    private static Set<String> aliases(String... values) {
        Set<String> result = new HashSet<>();
        for (String value : values) result.add(normalize(value));
        return Set.copyOf(result);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public record ProbeResult(boolean supported, String reason) {
        public static ProbeResult unsupported(String reason) {
            return new ProbeResult(false, reason);
        }
    }

    public record Result(boolean supported, SkinnedMesh mesh, String reason) {
        public static Result supported(SkinnedMesh mesh) {
            return new Result(true, mesh, "ok");
        }

        public static Result unsupported(String reason) {
            return new Result(false, null, reason);
        }
    }

    private record Resolution(
            List<BoneCandidate> bones,
            GeoBone chest,
            GeoBone head,
            GeoBone rightArm,
            GeoBone leftArm,
            GeoBone rightLeg,
            GeoBone leftLeg) {
    }

    private record BoneCandidate(GeoBone bone, int depth, String normalizedName) {
    }

    private record LimbTransformers(
            HumanoidModelTransformer.PartTransformer<GeoCube> rightArm,
            HumanoidModelTransformer.PartTransformer<GeoCube> leftArm,
            HumanoidModelTransformer.PartTransformer<GeoCube> rightLeg,
            HumanoidModelTransformer.PartTransformer<GeoCube> leftLeg) {
    }

    private record RuntimeGeoPart(String partName) implements MeshPartDefinition {
        @Override
        public Mesh.RenderProperties renderProperties() {
            return null;
        }

        @Override
        public Supplier<OpenMatrix4f> getModelPartAnimationProvider() {
            return () -> null;
        }
    }

    private GeckoHumanoidModelAdapter() {
    }
}
