package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.IntList;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.util.RenderUtils;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.MeshPartDefinition;
import yesman.epicfight.api.client.model.SingleGroupVertexBuilder;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.GeoModelTransformer;
import yesman.epicfight.api.client.model.transformer.HumanoidModelTransformer;
import yesman.epicfight.api.utils.math.OpenMatrix4f;

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
 * Generic GeckoLib humanoid analyser + Epic Fight mesh bridge.
 *
 * <p>EFPI only discovers the semantic humanoid regions. The actual cube
 * deformation is delegated to Epic Fight 20.14.x's own GeoModelTransformer
 * PartTransformers. This is important for one-bone Gecko limbs: Epic Fight's
 * LimbPartTransformer physically clips polygons at its elbow/knee plane and
 * inserts the intermediate joint instead of relying on a coarse per-vertex
 * weight approximation.</p>
 */
public final class GeckoHumanoidModelAdapter {
    private static final MeshPartDefinition PART = new RuntimeGeoPart("gecko_humanoid");

    private static final Set<String> BODY = aliases(
            "body", "torso", "chest", "upperbody", "upper_body", "trunk");
    private static final Set<String> HEAD_NAMES = aliases("head", "skull");
    private static final Set<String> RIGHT_ARM = aliases(
            "rightarm", "right_arm", "armright", "arm_right", "rarm", "armr",
            "rightupperarm", "upperarmright", "upper_arm_right");
    private static final Set<String> LEFT_ARM = aliases(
            "leftarm", "left_arm", "armleft", "arm_left", "larm", "arml",
            "leftupperarm", "upperarmleft", "upper_arm_left");
    private static final Set<String> RIGHT_LEG = aliases(
            "rightleg", "right_leg", "legright", "leg_right", "rleg",
            "rightthigh", "right_thigh", "thighright", "thigh_right");
    private static final Set<String> LEFT_LEG = aliases(
            "leftleg", "left_leg", "legleft", "leg_left", "lleg",
            "leftthigh", "left_thigh", "thighleft", "thigh_left");

    private static volatile EfTransformers efTransformers;

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

        try {
            for (GeoBone top : model.topLevelBones()) {
                resetToInitial(top);
            }

            EfTransformers transformers = transformers();
            SkinnedMesh mesh = bake(
                    body,
                    head,
                    rightArm,
                    leftArm,
                    rightLeg,
                    leftLeg,
                    transformers);

            return mesh == null
                    ? Result.unsupported("mesh bake returned null")
                    : Result.supported(mesh);
        } catch (Throwable throwable) {
            return Result.unsupported("mesh bake failed: " + throwable.getClass().getSimpleName());
        }
    }

    private static SkinnedMesh bake(
            GeoBone body,
            GeoBone head,
            GeoBone rightArm,
            GeoBone leftArm,
            GeoBone rightLeg,
            GeoBone leftLeg,
            EfTransformers transformers) {
        List<SingleGroupVertexBuilder> vertices = new ArrayList<>();
        Map<MeshPartDefinition, IntList> indices = new HashMap<>();
        HumanoidModelTransformer.PartTransformer.IndexCounter indexCounter =
                new HumanoidModelTransformer.PartTransformer.IndexCounter();

        Set<GeoBone> semanticRoots = Set.of(head, rightArm, leftArm, rightLeg, leftLeg);

        // The body root may be a zero-cube wrapper (Ancient Knight uses "body"
        // above "torso"). Walk it with CHEST while stopping at the other
        // semantic roots so geometry is neither duplicated nor assigned to the
        // wrong Epic Fight joint family.
        bakeRegion(
                body,
                transformers.chest(),
                semanticRoots,
                new PoseStack(),
                vertices,
                indices,
                indexCounter);

        bakeRegion(
                head,
                transformers.head(),
                Set.of(),
                new PoseStack(),
                vertices,
                indices,
                indexCounter);
        bakeRegion(
                rightArm,
                transformers.rightArm(),
                Set.of(),
                new PoseStack(),
                vertices,
                indices,
                indexCounter);
        bakeRegion(
                leftArm,
                transformers.leftArm(),
                Set.of(),
                new PoseStack(),
                vertices,
                indices,
                indexCounter);
        bakeRegion(
                rightLeg,
                transformers.rightLeg(),
                Set.of(),
                new PoseStack(),
                vertices,
                indices,
                indexCounter);
        bakeRegion(
                leftLeg,
                transformers.leftLeg(),
                Set.of(),
                new PoseStack(),
                vertices,
                indices,
                indexCounter);

        if (vertices.isEmpty()) {
            return null;
        }
        return SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
    }

    private static void bakeRegion(
            GeoBone bone,
            HumanoidModelTransformer.PartTransformer<GeoCube> transformer,
            Set<GeoBone> stopAtChildren,
            PoseStack stack,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices,
            HumanoidModelTransformer.PartTransformer.IndexCounter indexCounter) {
        if (bone == null || bone.isHidden()) {
            return;
        }

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

            // Delegate the actual mesh generation to Epic Fight. For arms and
            // legs this invokes GeoModelTransformer$LimbPartTransformer, which
            // splits quads at y=1.125 (elbow) / y=0.375 (knee) and assigns the
            // exact Arm/Hand/Elbow and Thigh/Leg/Knee joints used by BIPED.
            transformer.bakeCube(stack, PART, cube, vertices, indices, indexCounter);
            stack.popPose();
        }

        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                if (child == null || stopAtChildren.contains(child)) {
                    continue;
                }
                bakeRegion(
                        child,
                        transformer,
                        stopAtChildren,
                        stack,
                        vertices,
                        indices,
                        indexCounter);
            }
        }

        stack.popPose();
    }

    @SuppressWarnings("unchecked")
    private static EfTransformers transformers() throws ReflectiveOperationException {
        EfTransformers cached = efTransformers;
        if (cached != null) {
            return cached;
        }

        synchronized (GeckoHumanoidModelAdapter.class) {
            cached = efTransformers;
            if (cached != null) {
                return cached;
            }

            cached = new EfTransformers(
                    transformerField("HEAD"),
                    transformerField("CHEST"),
                    transformerField("RIGHT_ARM"),
                    transformerField("LEFT_ARM"),
                    transformerField("RIGHT_LEG"),
                    transformerField("LEFT_LEG"));
            efTransformers = cached;
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
        if (bone == null) {
            return;
        }

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
        if (bone == null) {
            return;
        }
        out.add(new BoneCandidate(bone, depth, normalize(bone.getName())));
        for (GeoBone child : bone.getChildBones()) {
            collect(child, depth + 1, out);
        }
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

    private static int score(String name, Set<String> aliases) {
        if (name == null || name.isEmpty()) {
            return 0;
        }

        int best = 0;
        for (String alias : aliases) {
            if (name.equals(alias)) {
                best = Math.max(best, 100);
            } else if (name.startsWith(alias) || name.endsWith(alias)) {
                best = Math.max(best, 82);
            }
        }
        return best;
    }

    private static boolean allDistinct(GeoBone... bones) {
        Set<GeoBone> set = new HashSet<>();
        for (GeoBone bone : bones) {
            if (bone == null || !set.add(bone)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRelated(GeoBone ancestorCandidate, GeoBone childCandidate) {
        GeoBone cursor = childCandidate;
        while (cursor != null) {
            if (cursor == ancestorCandidate) {
                return true;
            }
            cursor = cursor.getParent();
        }

        // Some models put head/arms beside the torso under a common wrapper.
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
        for (String value : values) {
            result.add(normalize(value));
        }
        return Set.copyOf(result);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
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

    private record EfTransformers(
            HumanoidModelTransformer.PartTransformer<GeoCube> head,
            HumanoidModelTransformer.PartTransformer<GeoCube> chest,
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
