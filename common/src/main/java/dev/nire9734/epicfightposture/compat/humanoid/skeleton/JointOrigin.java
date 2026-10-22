package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

/** Describes how a semantic joint was obtained from the source model. */
public enum JointOrigin {
    /** A source node directly represents this semantic joint. */
    SOURCE,
    /** The joint was inferred from an otherwise unambiguous source hierarchy. */
    INFERRED_FROM_HIERARCHY,
    /** The joint was inferred from source geometry and pivots. */
    INFERRED_FROM_GEOMETRY,
    /** The source rig has no corresponding node; EFPI synthesized the joint. */
    VIRTUAL
}
