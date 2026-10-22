package dev.nire9734.epicfightposture.neoforge.compat.models;

import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;

/** Small pure helper for adaptive tessellation of deforming source quads. */
final class SkinningSubdivision {
    private static final float WEIGHT_EPSILON = 1.0E-4F;
    private static final float SAMPLE_SPACING_PIXELS = 1.0F;
    private static final int MAX_SEGMENTS = 32;

    static boolean weightsDiffer(
            HumanoidSkinWeightResolver.SkinWeights first,
            HumanoidSkinWeightResolver.SkinWeights second) {
        if (first == null || second == null) {
            return true;
        }
        return first.count() != second.count()
                || first.firstJoint() != second.firstJoint()
                || first.secondJoint() != second.secondJoint()
                || Math.abs(first.firstWeight() - second.firstWeight()) > WEIGHT_EPSILON
                || Math.abs(first.secondWeight() - second.secondWeight()) > WEIGHT_EPSILON;
    }

    static int segmentCount(float edgeLengthPixels, boolean varyingSkin) {
        if (!varyingSkin || !Float.isFinite(edgeLengthPixels) || edgeLengthPixels <= SAMPLE_SPACING_PIXELS) {
            return 1;
        }
        int requested = (int) Math.ceil(edgeLengthPixels / SAMPLE_SPACING_PIXELS);
        return Math.max(1, Math.min(MAX_SEGMENTS, requested));
    }

    private SkinningSubdivision() {}
}
