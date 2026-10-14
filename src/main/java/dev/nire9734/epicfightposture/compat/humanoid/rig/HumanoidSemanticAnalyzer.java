package dev.nire9734.epicfightposture.compat.humanoid.rig;

/**
 * Converts a raw framework-neutral source rig into semantic humanoid candidates.
 *
 * <p>Implementations should score names, hierarchy, left/right placement, and
 * geometry without creating Epic Fight objects. The common resolver performs
 * virtual-joint completion and final topology validation.</p>
 */
@FunctionalInterface
public interface HumanoidSemanticAnalyzer {
    HumanoidSemanticAnalysis analyze(SourceRigSnapshot snapshot);
}
