package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

/** Summary of how much of a normalized rig came directly from the source model. */
public record RigQuality(
        float overallConfidence,
        int sourceJointCount,
        int inferredJointCount,
        int virtualJointCount,
        boolean asymmetric,
        boolean hadNonUniformBindScale
) {
    public RigQuality {
        if (!Float.isFinite(overallConfidence) || overallConfidence < 0.0F || overallConfidence > 1.0F) {
            throw new IllegalArgumentException("Overall confidence must be within [0, 1]");
        }
        if (sourceJointCount < 0 || inferredJointCount < 0 || virtualJointCount < 0) {
            throw new IllegalArgumentException("Rig quality counts must be non-negative");
        }
    }
}
