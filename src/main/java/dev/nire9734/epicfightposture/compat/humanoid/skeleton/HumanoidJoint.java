package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

/**
 * Library-independent semantic joints used by EFPI's humanoid retargeting pipeline.
 *
 * <p>{@link #ROOT} is the skeletal pelvis/hip-center root, not the renderer/model
 * origin. Source model hierarchies are normalized to this topology before any
 * Epic Fight mapping or animation retargeting is performed.</p>
 */
public enum HumanoidJoint {
    ROOT,

    SPINE,
    CHEST,
    NECK,
    HEAD,

    SHOULDER_LEFT,
    ELBOW_LEFT,
    WRIST_LEFT,

    SHOULDER_RIGHT,
    ELBOW_RIGHT,
    WRIST_RIGHT,

    HIP_LEFT,
    KNEE_LEFT,
    ANKLE_LEFT,

    HIP_RIGHT,
    KNEE_RIGHT,
    ANKLE_RIGHT
}
