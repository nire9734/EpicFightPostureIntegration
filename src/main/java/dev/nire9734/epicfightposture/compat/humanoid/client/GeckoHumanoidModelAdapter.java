package dev.nire9734.epicfightposture.compat.humanoid.client;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.state.BoneSnapshot;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.GeoModelTransformer;
import yesman.epicfight.api.client.model.transformer.HumanoidModelTransformer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generic GeckoLib humanoid analyser + Epic Fight mesh bridge.
 *
 * <p>EFPI only identifies semantic humanoid regions. Once those regions are
 * known, the actual mesh conversion is delegated to Epic Fight 20.14.x's full
 * GeoModelTransformer bake pipeline, including GeoMeshPartDefinition metadata,
 * polygon clipping and limb joint assignment.</p>
 */
public final class GeckoHumanoidModelAdapter {
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

    private static volatile EfGeoBridge efGeoBridge;

    public static Result analyseAndBake(BakedGeoModel model) {
        if (model == null || model.topLevelBones() == null || model.topLevelBones().isEmpty()) {
            return Result.unsupported("empty model");
        }

        List<BoneCandidate> bones = new ArrayList<>();
        for (GeoBone root : model.topLevelBones()) {
            collect(root, 0, bones);
        }

        GeoBone body = findBestBody(bones);
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

            SkinnedMesh mesh = bakeWithEpicFight(
                    body,
                    head,
                    rightArm,
                    leftArm,
                    rightLeg,
                    leftLeg,
                    bridge());

            return mesh == null
                    ? Result.unsupported("Epic Fight Geo bake returned null")
                    : Result.supported(mesh);
        } catch (Throwable throwable) {
            return Result.unsupported("Epic Fight Geo bake failed: " + throwable.getClass().getSimpleName());
        }
    }

    /**
     * Epic Fight's armor Geo transformer expects each body region as an
     * independent partition. Entity Geo rigs often nest head/arms under torso,
     * so those semantic roots are detached only for the duration of the bake to
     * avoid the chest partition recursively baking them a second time.
     */
    private static SkinnedMesh bakeWithEpicFight(
            GeoBone body,
            GeoBone head,
            GeoBone rightArm,
            GeoBone leftArm,
            GeoBone rightLeg,
            GeoBone leftLeg,
            EfGeoBridge bridge) throws ReflectiveOperationException {
        Set<GeoBone> independentRoots = Set.of(head, rightArm, leftArm, rightLeg, leftLeg);
        List<DetachedBone> detached = new ArrayList<>();

        detachNestedSemanticRoots(body, independentRoots, detached);

        try {
            List<Object> partitions = new ArrayList<>(6);
            partitions.add(bridge.newPartition(bridge.head(), head));
            partitions.add(bridge.newPartition(bridge.chest(), body));
            partitions.add(bridge.newPartition(bridge.rightArm(), rightArm));
            partitions.add(bridge.newPartition(bridge.leftArm(), leftArm));
            partitions.add(bridge.newPartition(bridge.leftLeg(), leftLeg));
            partitions.add(bridge.newPartition(bridge.rightLeg(), rightLeg));

            Object baked = bridge.bakeMesh().invoke(null, partitions);
            return baked instanceof SkinnedMesh skinnedMesh ? skinnedMesh : null;
        } finally {
            // We detached in reverse child-index order. Restore in reverse record
            // order so each original insertion index becomes valid again.
            for (int i = detached.size() - 1; i >= 0; i--) {
                DetachedBone entry = detached.get(i);
                List<GeoBone> children = entry.parent().getChildBones();
                int index = Math.max(0, Math.min(entry.index(), children.size()));
                children.add(index, entry.bone());
            }
        }
    }

    private static void detachNestedSemanticRoots(
            GeoBone parent,
            Set<GeoBone> semanticRoots,
            List<DetachedBone> detached) {
        if (parent == null || parent.isHidingChildren()) {
            return;
        }

        List<GeoBone> children = parent.getChildBones();
        for (int i = children.size() - 1; i >= 0; i--) {
            GeoBone child = children.get(i);
            if (semanticRoots.contains(child)) {
                children.remove(i);
                detached.add(new DetachedBone(parent, child, i));
            } else {
                detachNestedSemanticRoots(child, semanticRoots, detached);
            }
        }
    }

    private static EfGeoBridge bridge() throws ReflectiveOperationException {
        EfGeoBridge cached = efGeoBridge;
        if (cached != null) {
            return cached;
        }

        synchronized (GeckoHumanoidModelAdapter.class) {
            cached = efGeoBridge;
            if (cached != null) {
                return cached;
            }

            Class<?> partitionClass = Class.forName(
                    "yesman.epicfight.api.client.model.transformer.GeoModelTransformer$GeoModelPartition");
            Constructor<?> partitionConstructor = partitionClass.getDeclaredConstructor(
                    HumanoidModelTransformer.PartTransformer.class,
                    GeoBone.class);
            partitionConstructor.setAccessible(true);

            Method bakeMesh = GeoModelTransformer.class.getDeclaredMethod("bakeMeshFromCubes", List.class);
            bakeMesh.setAccessible(true);

            cached = new EfGeoBridge(
                    partitionConstructor,
                    bakeMesh,
                    transformerField("HEAD"),
                    transformerField("CHEST"),
                    transformerField("RIGHT_ARM"),
                    transformerField("LEFT_ARM"),
                    transformerField("RIGHT_LEG"),
                    transformerField("LEFT_LEG"));
            efGeoBridge = cached;
            return cached;
        }
    }

    @SuppressWarnings("unchecked")
    private static HumanoidModelTransformer.PartTransformer<?> transformerField(String name)
            throws ReflectiveOperationException {
        Field field = GeoModelTransformer.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(null);
        if (!(value instanceof HumanoidModelTransformer.PartTransformer<?> transformer)) {
            throw new ReflectiveOperationException("Unexpected Epic Fight transformer field: " + name);
        }
        return transformer;
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

    /** Prefer the actual geometry-bearing torso over a zero-cube wrapper named body. */
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

    private record DetachedBone(GeoBone parent, GeoBone bone, int index) {
    }

    private record EfGeoBridge(
            Constructor<?> partitionConstructor,
            Method bakeMesh,
            HumanoidModelTransformer.PartTransformer<?> head,
            HumanoidModelTransformer.PartTransformer<?> chest,
            HumanoidModelTransformer.PartTransformer<?> rightArm,
            HumanoidModelTransformer.PartTransformer<?> leftArm,
            HumanoidModelTransformer.PartTransformer<?> rightLeg,
            HumanoidModelTransformer.PartTransformer<?> leftLeg) {
        Object newPartition(HumanoidModelTransformer.PartTransformer<?> transformer, GeoBone bone)
                throws ReflectiveOperationException {
            return this.partitionConstructor.newInstance(transformer, bone);
        }
    }

    private GeckoHumanoidModelAdapter() {
    }
}
