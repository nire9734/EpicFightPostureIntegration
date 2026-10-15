package dev.nire9734.epicfightposture.compat.humanoid.retarget;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Framework-neutral deformation decision for secondary humanoid geometry.
 *
 * <p>The policy deliberately consumes only already-resolved skin weights. Source
 * frameworks remain responsible for extracting geometry, authored bind transforms,
 * visibility and render lifecycle. If every sampled point belongs to the same
 * canonical deformation segment, rigid replay is sufficient. If samples cross a
 * supported humanoid transition, the geometry must remain deformable and use the
 * per-vertex HumanoidSkinWeightResolver result.</p>
 *
 * <p>Unsupported or ambiguous multi-chain samples return {@link Optional#empty()}
 * so framework bridges can fail closed to their existing rigid/custom fallback.</p>
 */
public final class SecondaryDeformationPolicy {
    private static final float WEIGHT_EPSILON = 1.0E-4F;

    public enum Mode {
        RIGID_ATTACHMENT,
        DEFORMABLE
    }

    public record Decision(Mode mode, int rigidJointId) {
        public Decision {
            Objects.requireNonNull(mode, "mode");
            if (mode == Mode.RIGID_ATTACHMENT && rigidJointId < 0) {
                throw new IllegalArgumentException("Rigid secondary deformation requires a joint id");
            }
        }

        public static Decision rigid(int jointId) {
            return new Decision(Mode.RIGID_ATTACHMENT, jointId);
        }

        public static Decision deformable() {
            return new Decision(Mode.DEFORMABLE, -1);
        }

        public boolean rigid() {
            return mode == Mode.RIGID_ATTACHMENT;
        }
    }

    /**
     * Classifies one geometry primitive from skin-weight samples at its real
     * model-space positions.
     *
     * <p>A local accessory may contain a small amount of blend near a synthetic
     * joint but still belong entirely to one side of that joint. We therefore use
     * the dominant joint at each sample to decide whether the primitive actually
     * spans two body segments. This keeps hand-local plates rigid while a sleeve
     * whose samples reach both upper arm and forearm remains deformable.</p>
     */
    public static Optional<Decision> classify(
            Iterable<HumanoidSkinWeightResolver.SkinWeights> samples) {
        Objects.requireNonNull(samples, "samples");

        Set<Integer> dominantJoints = new LinkedHashSet<>();
        Set<Integer> involvedJoints = new LinkedHashSet<>();
        boolean any = false;

        for (HumanoidSkinWeightResolver.SkinWeights skin : samples) {
            if (skin == null) {
                return Optional.empty();
            }
            any = true;

            dominantJoints.add(dominantJoint(skin));
            involvedJoints.add(skin.firstJoint());
            if (skin.count() > 1 && skin.secondWeight() > WEIGHT_EPSILON) {
                involvedJoints.add(skin.secondJoint());
            }

            if (involvedJoints.size() > 2) {
                return Optional.empty();
            }
        }

        if (!any || dominantJoints.isEmpty()) {
            return Optional.empty();
        }
        if (involvedJoints.size() == 2) {
            Integer[] pair = involvedJoints.toArray(Integer[]::new);
            if (!isCanonicalTransition(pair[0], pair[1])) {
                return Optional.empty();
            }
        }

        if (dominantJoints.size() == 1) {
            return Optional.of(Decision.rigid(dominantJoints.iterator().next()));
        }
        if (dominantJoints.size() == 2) {
            Integer[] pair = dominantJoints.toArray(Integer[]::new);
            if (isCanonicalTransition(pair[0], pair[1])) {
                return Optional.of(Decision.deformable());
            }
        }
        return Optional.empty();
    }

    public static int dominantJoint(HumanoidSkinWeightResolver.SkinWeights skin) {
        Objects.requireNonNull(skin, "skin");
        if (skin.count() == 1 || skin.firstWeight() >= skin.secondWeight()) {
            return skin.firstJoint();
        }
        return skin.secondJoint();
    }

    public static boolean isCanonicalTransition(int first, int second) {
        if (first == second) {
            return true;
        }
        return pair(first, second, HumanoidSkinWeightResolver.ROOT, HumanoidSkinWeightResolver.CHEST)
                || pair(first, second, HumanoidSkinWeightResolver.ARM_RIGHT, HumanoidSkinWeightResolver.HAND_RIGHT)
                || pair(first, second, HumanoidSkinWeightResolver.ARM_LEFT, HumanoidSkinWeightResolver.HAND_LEFT)
                || pair(first, second, HumanoidSkinWeightResolver.THIGH_RIGHT, HumanoidSkinWeightResolver.LEG_RIGHT)
                || pair(first, second, HumanoidSkinWeightResolver.THIGH_LEFT, HumanoidSkinWeightResolver.LEG_LEFT);
    }

    private static boolean pair(int first, int second, int a, int b) {
        return (first == a && second == b) || (first == b && second == a);
    }

    private SecondaryDeformationPolicy() {
    }
}
