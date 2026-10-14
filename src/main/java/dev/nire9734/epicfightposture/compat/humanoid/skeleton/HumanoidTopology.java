package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed semantic topology shared by every completed humanoid descriptor. */
public final class HumanoidTopology {
    private static final Map<HumanoidJoint, HumanoidJoint> PARENTS;

    public static final List<HumanoidJoint> ORDER = List.of(
            HumanoidJoint.ROOT,
            HumanoidJoint.PELVIS,
            HumanoidJoint.SPINE,
            HumanoidJoint.CHEST,
            HumanoidJoint.NECK,
            HumanoidJoint.HEAD,
            HumanoidJoint.SHOULDER_LEFT,
            HumanoidJoint.ELBOW_LEFT,
            HumanoidJoint.WRIST_LEFT,
            HumanoidJoint.SHOULDER_RIGHT,
            HumanoidJoint.ELBOW_RIGHT,
            HumanoidJoint.WRIST_RIGHT,
            HumanoidJoint.HIP_LEFT,
            HumanoidJoint.KNEE_LEFT,
            HumanoidJoint.ANKLE_LEFT,
            HumanoidJoint.HIP_RIGHT,
            HumanoidJoint.KNEE_RIGHT,
            HumanoidJoint.ANKLE_RIGHT
    );

    public static final Set<HumanoidJoint> REQUIRED = Set.copyOf(ORDER);

    static {
        EnumMap<HumanoidJoint, HumanoidJoint> parents = new EnumMap<>(HumanoidJoint.class);
        parents.put(HumanoidJoint.PELVIS, HumanoidJoint.ROOT);
        parents.put(HumanoidJoint.SPINE, HumanoidJoint.PELVIS);
        parents.put(HumanoidJoint.CHEST, HumanoidJoint.SPINE);
        parents.put(HumanoidJoint.NECK, HumanoidJoint.CHEST);
        parents.put(HumanoidJoint.HEAD, HumanoidJoint.NECK);

        parents.put(HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.CHEST);
        parents.put(HumanoidJoint.ELBOW_LEFT, HumanoidJoint.SHOULDER_LEFT);
        parents.put(HumanoidJoint.WRIST_LEFT, HumanoidJoint.ELBOW_LEFT);

        parents.put(HumanoidJoint.SHOULDER_RIGHT, HumanoidJoint.CHEST);
        parents.put(HumanoidJoint.ELBOW_RIGHT, HumanoidJoint.SHOULDER_RIGHT);
        parents.put(HumanoidJoint.WRIST_RIGHT, HumanoidJoint.ELBOW_RIGHT);

        parents.put(HumanoidJoint.HIP_LEFT, HumanoidJoint.PELVIS);
        parents.put(HumanoidJoint.KNEE_LEFT, HumanoidJoint.HIP_LEFT);
        parents.put(HumanoidJoint.ANKLE_LEFT, HumanoidJoint.KNEE_LEFT);

        parents.put(HumanoidJoint.HIP_RIGHT, HumanoidJoint.PELVIS);
        parents.put(HumanoidJoint.KNEE_RIGHT, HumanoidJoint.HIP_RIGHT);
        parents.put(HumanoidJoint.ANKLE_RIGHT, HumanoidJoint.KNEE_RIGHT);
        PARENTS = Map.copyOf(parents);
    }

    public static HumanoidJoint parentOf(HumanoidJoint joint) {
        return PARENTS.get(joint);
    }

    public static int depth(HumanoidJoint joint) {
        int depth = 0;
        HumanoidJoint cursor = joint;
        while ((cursor = parentOf(cursor)) != null) {
            depth++;
        }
        return depth;
    }

    private HumanoidTopology() {
    }
}
