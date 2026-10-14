package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Completed, library-independent humanoid bind skeleton.
 *
 * <p>All joint transforms are expressed in EFPI model space: +X entity-right,
 * +Y up, +Z entity-forward, with one unit equal to one Minecraft block. Source
 * adapters must remove runtime animation and bake source bind scale before
 * constructing this descriptor.</p>
 */
public final class HumanoidSkeletonDescriptor {
    private final SkeletonKey key;
    private final Map<HumanoidJoint, JointDescriptor> joints;
    private final BodyMetrics metrics;
    private final BodyBounds bounds;
    private final RigQuality quality;

    public HumanoidSkeletonDescriptor(
            SkeletonKey key,
            Map<HumanoidJoint, JointDescriptor> joints,
            BodyBounds bounds,
            RigQuality quality) {
        this.key = Objects.requireNonNull(key, "key");
        Objects.requireNonNull(joints, "joints");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.quality = Objects.requireNonNull(quality, "quality");

        EnumMap<HumanoidJoint, JointDescriptor> copy = new EnumMap<>(HumanoidJoint.class);
        copy.putAll(joints);
        for (HumanoidJoint required : HumanoidTopology.REQUIRED) {
            JointDescriptor descriptor = copy.get(required);
            if (descriptor == null) {
                throw new IllegalArgumentException("Completed humanoid descriptor is missing " + required);
            }
            if (descriptor.joint() != required) {
                throw new IllegalArgumentException("Joint map key does not match descriptor semantic joint: " + required);
            }
        }
        this.joints = Map.copyOf(copy);
        this.metrics = BodyMetrics.derive(this.joints, this.bounds);
    }

    public SkeletonKey key() {
        return key;
    }

    public Map<HumanoidJoint, JointDescriptor> joints() {
        return joints;
    }

    public JointDescriptor joint(HumanoidJoint joint) {
        JointDescriptor descriptor = joints.get(joint);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown semantic joint " + joint);
        }
        return descriptor;
    }

    public RigidTransform bindModelTransform(HumanoidJoint joint) {
        return joint(joint).bindModelTransform();
    }

    /** Derives parent-local bind transform from the authoritative model-space bind pose. */
    public RigidTransform bindLocalTransform(HumanoidJoint joint) {
        HumanoidJoint parent = HumanoidTopology.parentOf(joint);
        RigidTransform model = bindModelTransform(joint);
        return parent == null ? model : model.relativeTo(bindModelTransform(parent));
    }

    public BodyMetrics metrics() {
        return metrics;
    }

    public BodyBounds bounds() {
        return bounds;
    }

    public RigQuality quality() {
        return quality;
    }
}
