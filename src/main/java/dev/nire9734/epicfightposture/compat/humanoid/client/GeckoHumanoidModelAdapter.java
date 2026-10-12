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
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec2f;
import yesman.epicfight.api.utils.math.Vec3f;

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
 * <p>Known lower-arm/lower-leg bones are mapped directly to Epic Fight's
 * lower-limb joints. If a Gecko rig only has a single arm/leg bone, the
 * geometry is weighted along the limb so Epic Fight can still bend it around
 * its synthetic elbow/knee joints.</p>
 */
public final class GeckoHumanoidModelAdapter {
    private static final MeshPartDefinition PART = new RuntimeGeoPart("gecko_humanoid");

    private static final int ROOT = 0;
    private static final int THIGH_R = 1;
    private static final int LEG_R = 2;
    private static final int KNEE_R = 3;
    private static final int THIGH_L = 4;
    private static final int LEG_L = 5;
    private static final int KNEE_L = 6;
    private static final int CHEST = 8;
    private static final int HEAD = 9;
    private static final int ARM_R = 11;
    private static final int HAND_R = 12;
    private static final int ELBOW_R = 14;
    private static final int ARM_L = 16;
    private static final int HAND_L = 17;
    private static final int ELBOW_L = 19;

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
            "lower_right_arm", "rightarm2", "right_arm_2", "armright2", "arm_right_2",
            "rforearm", "forearmr", "forearm_r");
    private static final Set<String> LEFT_FOREARM = aliases(
            "leftforearm", "left_forearm", "forearmleft", "forearm_left",
            "leftlowerarm", "left_lower_arm", "lowerarmleft", "lower_arm_left",
            "lower_left_arm", "leftarm2", "left_arm_2", "armleft2", "arm_left_2",
            "lforearm", "forearml", "forearm_l");

    private static final Set<String> RIGHT_ELBOW = aliases(
            "rightelbow", "right_elbow", "elbowright", "elbow_right", "relbow");
    private static final Set<String> LEFT_ELBOW = aliases(
            "leftelbow", "left_elbow", "elbowleft", "elbow_left", "lelbow");
    private static final Set<String> RIGHT_HAND_NAMES = aliases(
            "righthand", "right_hand", "handright", "hand_right", "rhand", "handr");
    private static final Set<String> LEFT_HAND_NAMES = aliases(
            "lefthand", "left_hand", "handleft", "hand_left", "lhand", "handl");

    private static final Set<String> RIGHT_LEG = aliases(
            "rightleg", "right_leg", "legright", "leg_right", "rleg",
            "rightthigh", "right_thigh", "thighright", "thigh_right");
    private static final Set<String> LEFT_LEG = aliases(
            "leftleg", "left_leg", "legleft", "leg_left", "lleg",
            "leftthigh", "left_thigh", "thighleft", "thigh_left");

    private static final Set<String> RIGHT_LOWER_LEG = aliases(
            "rightlowerleg", "right_lower_leg", "lowerlegright", "lower_leg_right",
            "lower_right_leg", "rightshin", "right_shin", "shinright", "shin_right",
            "rightcalf", "right_calf", "calfright", "calf_right",
            "rightleg2", "right_leg_2", "rshin", "calfr");
    private static final Set<String> LEFT_LOWER_LEG = aliases(
            "leftlowerleg", "left_lower_leg", "lowerlegleft", "lower_leg_left",
            "lower_left_leg", "leftshin", "left_shin", "shinleft", "shin_left",
            "leftcalf", "left_calf", "calfleft", "calf_left",
            "leftleg2", "left_leg_2", "lshin", "calfl");

    private static final Set<String> RIGHT_KNEE = aliases(
            "rightknee", "right_knee", "kneeright", "knee_right", "rknee");
    private static final Set<String> LEFT_KNEE = aliases(
            "leftknee", "left_knee", "kneeleft", "knee_left", "lknee");
    private static final Set<String> RIGHT_FOOT = aliases(
            "rightfoot", "right_foot", "footright", "foot_right", "rfoot", "footr");
    private static final Set<String> LEFT_FOOT = aliases(
            "leftfoot", "left_foot", "footleft", "foot_left", "lfoot", "footl");

    private static final Set<String> ROOT_NAMES = aliases("root", "master", "base", "main");

    public static Result analyseAndBake(BakedGeoModel model) {
        if (model == null || model.topLevelBones() == null || model.topLevelBones().isEmpty()) {
            return Result.unsupported("empty model");
        }

        List<BoneCandidate> bones = new ArrayList<>();
        for (GeoBone root : model.topLevelBones()) {
            collect(root, 0, bones);
        }

        GeoBone body = findBest(bones, BODY);
        GeoBone head = findBest(bones, HEAD_NAMES);
        GeoBone rightArm = findBest(bones, RIGHT_ARM);
        GeoBone leftArm = findBest(bones, LEFT_ARM);
        GeoBone rightLeg = findBest(bones, RIGHT_LEG);
        GeoBone leftLeg = findBest(bones, LEFT_LEG);

        if (!allDistinct(body, head, rightArm, leftArm, rightLeg, leftLeg)) {
            return Result.unsupported("essential humanoid bones were missing or ambiguous");
        }

        if (!isRelated(body, head) || !isRelated(body, rightArm) || !isRelated(body, leftArm)) {
            return Result.unsupported("head/arm hierarchy is not humanoid-like");
        }

        Map<GeoBone, Integer> joints = new HashMap<>();
        Map<GeoBone, VirtualLimb> virtualLimbs = new HashMap<>();

        GeoBone root = findBest(bones, ROOT_NAMES);
        if (root == null) root = topAncestor(body);

        if (root != null) joints.put(root, ROOT);
        joints.put(body, CHEST);
        joints.put(head, HEAD);
        joints.put(rightArm, ARM_R);
        joints.put(leftArm, ARM_L);
        joints.put(rightLeg, THIGH_R);
        joints.put(leftLeg, THIGH_L);

        GeoBone rightForearm = findDescendantBest(rightArm, RIGHT_FOREARM);
        GeoBone leftForearm = findDescendantBest(leftArm, LEFT_FOREARM);
        GeoBone rightElbow = findDescendantBest(rightArm, RIGHT_ELBOW);
        GeoBone leftElbow = findDescendantBest(leftArm, LEFT_ELBOW);
        GeoBone rightHand = findDescendantBest(rightArm, RIGHT_HAND_NAMES);
        GeoBone leftHand = findDescendantBest(leftArm, LEFT_HAND_NAMES);

        GeoBone rightLowerLeg = findDescendantBest(rightLeg, RIGHT_LOWER_LEG);
        GeoBone leftLowerLeg = findDescendantBest(leftLeg, LEFT_LOWER_LEG);
        GeoBone rightKnee = findDescendantBest(rightLeg, RIGHT_KNEE);
        GeoBone leftKnee = findDescendantBest(leftLeg, LEFT_KNEE);
        GeoBone rightFoot = findDescendantBest(rightLeg, RIGHT_FOOT);
        GeoBone leftFoot = findDescendantBest(leftLeg, LEFT_FOOT);

        if (rightForearm != null) joints.put(rightForearm, HAND_R);
        else virtualLimbs.put(rightArm, new VirtualLimb(ARM_R, HAND_R, ELBOW_R));

        if (leftForearm != null) joints.put(leftForearm, HAND_L);
        else virtualLimbs.put(leftArm, new VirtualLimb(ARM_L, HAND_L, ELBOW_L));

        if (rightLowerLeg != null) joints.put(rightLowerLeg, LEG_R);
        else virtualLimbs.put(rightLeg, new VirtualLimb(THIGH_R, LEG_R, KNEE_R));

        if (leftLowerLeg != null) joints.put(leftLowerLeg, LEG_L);
        else virtualLimbs.put(leftLeg, new VirtualLimb(THIGH_L, LEG_L, KNEE_L));

        putIfDistinct(joints, rightElbow, ELBOW_R, rightForearm, rightHand);
        putIfDistinct(joints, leftElbow, ELBOW_L, leftForearm, leftHand);
        putIfDistinct(joints, rightKnee, KNEE_R, rightLowerLeg, rightFoot);
        putIfDistinct(joints, leftKnee, KNEE_L, leftLowerLeg, leftFoot);

        if (rightHand != null) joints.put(rightHand, HAND_R);
        if (leftHand != null) joints.put(leftHand, HAND_L);
        if (rightFoot != null) joints.put(rightFoot, LEG_R);
        if (leftFoot != null) joints.put(leftFoot, LEG_L);

        try {
            for (GeoBone top : model.topLevelBones()) resetToInitial(top);

            SkinnedMesh mesh = bake(model, joints, virtualLimbs);
            return mesh == null
                    ? Result.unsupported("mesh bake returned null")
                    : Result.supported(mesh);
        } catch (Throwable throwable) {
            return Result.unsupported("mesh bake failed: " + throwable.getClass().getSimpleName());
        }
    }

    private static SkinnedMesh bake(
            BakedGeoModel model,
            Map<GeoBone, Integer> joints,
            Map<GeoBone, VirtualLimb> virtualLimbs) {
        List<SingleGroupVertexBuilder> vertices = new ArrayList<>();
        Map<MeshPartDefinition, IntList> indices = new HashMap<>();
        int[] indexCounter = {0};
        PoseStack stack = new PoseStack();

        for (GeoBone top : model.topLevelBones()) {
            bakeBone(top, ROOT, joints, virtualLimbs, stack, vertices, indices, indexCounter);
        }

        if (vertices.isEmpty()) return null;
        return SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
    }

    private static void bakeBone(
            GeoBone bone,
            int inheritedJoint,
            Map<GeoBone, Integer> joints,
            Map<GeoBone, VirtualLimb> virtualLimbs,
            PoseStack stack,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices,
            int[] indexCounter) {
        if (bone == null || bone.isHidden()) return;

        int joint = joints.getOrDefault(bone, inheritedJoint);
        stack.pushPose();
        RenderUtils.prepMatrixForBone(stack, bone);

        VirtualLimb limb = virtualLimbs.get(bone);
        BendProfile bendProfile = limb == null ? null : measureBendProfile(bone, stack, limb);

        for (GeoCube cube : bone.getCubes()) {
            stack.pushPose();
            RenderUtils.translateToPivotPoint(stack, cube);
            RenderUtils.rotateMatrixAroundCube(stack, cube);
            RenderUtils.translateAwayFromPivotPoint(stack, cube);

            GeoQuad[] quads = cube.quads();
            if (quads != null) {
                for (GeoQuad quad : quads) {
                    if (quad == null) continue;

                    Vector3f normal = new Vector3f(quad.normal());
                    normal.mul(stack.last().normal());

                    for (GeoVertex vertex : quad.vertices()) {
                        Vector4f pos = new Vector4f(vertex.position(), 1.0F);
                        pos.mul(stack.last().pose());

                        JointBinding binding = bendProfile == null
                                ? JointBinding.single(joint)
                                : bendProfile.bindingFor(pos);

                        vertices.add(new SingleGroupVertexBuilder()
                                .setPosition(new Vec3f(pos.x(), pos.y(), pos.z()))
                                .setNormal(new Vec3f(normal.x(), normal.y(), normal.z()))
                                .setTextureCoordinate(new Vec2f(vertex.texU(), vertex.texV()))
                                .setEffectiveJointIDs(new Vec3f(
                                        binding.joint0(), binding.joint1(), binding.joint2()))
                                .setEffectiveJointWeights(new Vec3f(
                                        binding.weight0(), binding.weight1(), binding.weight2()))
                                .setEffectiveJointNumber(binding.count()));
                    }

                    triangulateQuad(indices, PART, indexCounter);
                }
            }
            stack.popPose();
        }

        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                bakeBone(child, joint, joints, virtualLimbs, stack, vertices, indices, indexCounter);
            }
        }

        stack.popPose();
    }

    private static BendProfile measureBendProfile(
            GeoBone bone,
            PoseStack stack,
            VirtualLimb limb) {
        Vector4f pivot4 = new Vector4f(
                bone.getPivotX() / 16.0F,
                bone.getPivotY() / 16.0F,
                bone.getPivotZ() / 16.0F,
                1.0F);
        pivot4.mul(stack.last().pose());
        Vector3f pivot = new Vector3f(pivot4.x(), pivot4.y(), pivot4.z());

        Vector3f centroid = new Vector3f();
        int count = 0;
        List<Vector3f> positions = new ArrayList<>();

        for (GeoCube cube : bone.getCubes()) {
            stack.pushPose();
            RenderUtils.translateToPivotPoint(stack, cube);
            RenderUtils.rotateMatrixAroundCube(stack, cube);
            RenderUtils.translateAwayFromPivotPoint(stack, cube);

            GeoQuad[] quads = cube.quads();
            if (quads != null) {
                for (GeoQuad quad : quads) {
                    if (quad == null) continue;
                    for (GeoVertex vertex : quad.vertices()) {
                        Vector4f pos4 = new Vector4f(vertex.position(), 1.0F);
                        pos4.mul(stack.last().pose());
                        Vector3f pos = new Vector3f(pos4.x(), pos4.y(), pos4.z());
                        positions.add(pos);
                        centroid.add(pos);
                        count++;
                    }
                }
            }

            stack.popPose();
        }

        if (count == 0) return null;

        centroid.div((float) count);
        Vector3f axis = new Vector3f(
                centroid.x() - pivot.x(),
                centroid.y() - pivot.y(),
                centroid.z() - pivot.z());

        if (axis.lengthSquared() < 1.0E-6F) {
            float bestDistance = 0.0F;
            Vector3f best = null;

            for (Vector3f pos : positions) {
                float dx = pos.x() - pivot.x();
                float dy = pos.y() - pivot.y();
                float dz = pos.z() - pivot.z();
                float distance = dx * dx + dy * dy + dz * dz;
                if (distance > bestDistance) {
                    bestDistance = distance;
                    best = pos;
                }
            }

            if (best == null || bestDistance < 1.0E-6F) return null;
            axis.set(best.x() - pivot.x(), best.y() - pivot.y(), best.z() - pivot.z());
        }

        axis.normalize();

        float extent = 0.0F;
        for (Vector3f pos : positions) {
            float projection =
                    (pos.x() - pivot.x()) * axis.x()
                    + (pos.y() - pivot.y()) * axis.y()
                    + (pos.z() - pivot.z()) * axis.z();
            extent = Math.max(extent, projection);
        }

        if (extent < 1.0E-4F) return null;
        return new BendProfile(pivot, axis, extent, limb);
    }

    private static void putIfDistinct(
            Map<GeoBone, Integer> joints,
            GeoBone bone,
            int joint,
            GeoBone... exclusions) {
        if (bone == null) return;
        for (GeoBone exclusion : exclusions) {
            if (bone == exclusion) return;
        }
        joints.put(bone, joint);
    }

    private static void triangulateQuad(
            Map<MeshPartDefinition, IntList> indices,
            MeshPartDefinition part,
            int[] indexCounter) {
        IntList list = indices.computeIfAbsent(part, key -> new IntArrayList());
        int first = indexCounter[0];
        int second = first + 1;
        int third = first + 2;
        int fourth = first + 3;

        addIndexTriplet(list, first);
        addIndexTriplet(list, second);
        addIndexTriplet(list, fourth);
        addIndexTriplet(list, fourth);
        addIndexTriplet(list, second);
        addIndexTriplet(list, third);
        indexCounter[0] += 4;
    }

    private static void addIndexTriplet(IntList list, int index) {
        list.add(index);
        list.add(index);
        list.add(index);
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
        for (GeoBone child : bone.getChildBones()) resetToInitial(child);
    }

    private static void collect(GeoBone bone, int depth, List<BoneCandidate> out) {
        if (bone == null) return;
        out.add(new BoneCandidate(bone, depth, normalize(bone.getName())));
        for (GeoBone child : bone.getChildBones()) collect(child, depth + 1, out);
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
        for (GeoBone child : parent.getChildBones()) collect(child, 1, descendants);
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
        while (cursor != null && cursor.getParent() != null) cursor = cursor.getParent();
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

    public record Result(boolean supported, SkinnedMesh mesh, String reason) {
        public static Result supported(SkinnedMesh mesh) {
            return new Result(true, mesh, "ok");
        }

        public static Result unsupported(String reason) {
            return new Result(false, null, reason);
        }
    }

    private record BoneCandidate(GeoBone bone, int depth, String normalizedName) {
    }

    private record VirtualLimb(int upperJoint, int lowerJoint, int middleJoint) {
    }

    private record BendProfile(
            Vector3f pivot,
            Vector3f axis,
            float extent,
            VirtualLimb limb) {
        JointBinding bindingFor(Vector4f position) {
            float projection =
                    (position.x() - this.pivot.x()) * this.axis.x()
                    + (position.y() - this.pivot.y()) * this.axis.y()
                    + (position.z() - this.pivot.z()) * this.axis.z();
            float t = Math.max(0.0F, Math.min(1.0F, projection / this.extent));

            if (t <= 0.42F) return JointBinding.single(this.limb.upperJoint());
            if (t >= 0.58F) return JointBinding.single(this.limb.lowerJoint());

            if (t <= 0.50F) {
                float middleWeight = (t - 0.42F) / 0.08F;
                return JointBinding.dual(
                        this.limb.upperJoint(),
                        this.limb.middleJoint(),
                        1.0F - middleWeight,
                        middleWeight);
            }

            float lowerWeight = (t - 0.50F) / 0.08F;
            return JointBinding.dual(
                    this.limb.middleJoint(),
                    this.limb.lowerJoint(),
                    1.0F - lowerWeight,
                    lowerWeight);
        }
    }

    private record JointBinding(
            int joint0,
            int joint1,
            int joint2,
            float weight0,
            float weight1,
            float weight2,
            int count) {
        static JointBinding single(int joint) {
            return new JointBinding(joint, 0, 0, 1.0F, 0.0F, 0.0F, 1);
        }

        static JointBinding dual(int joint0, int joint1, float weight0, float weight1) {
            return new JointBinding(joint0, joint1, 0, weight0, weight1, 0.0F, 2);
        }
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
