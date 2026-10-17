package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.Objects;

/**
 * Scale-free rigid transform in EFPI model space.
 *
 * <p>Bind scale is deliberately excluded. Source adapters must bake scale into
 * joint positions and mesh geometry before producing a descriptor.</p>
 */
public record RigidTransform(SkeletonVec3 position, SkeletonQuat rotation) {
    public static final RigidTransform IDENTITY = new RigidTransform(SkeletonVec3.ZERO, SkeletonQuat.IDENTITY);

    public RigidTransform {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
    }

    /** Returns {@code parentModel * local}. */
    public static RigidTransform compose(RigidTransform parentModel, RigidTransform local) {
        SkeletonVec3 position = parentModel.position.add(parentModel.rotation.rotate(local.position));
        SkeletonQuat rotation = parentModel.rotation.multiply(local.rotation);
        return new RigidTransform(position, rotation);
    }

    /**
     * Converts this model-space transform to a transform local to the supplied
     * model-space parent.
     */
    public RigidTransform relativeTo(RigidTransform parentModel) {
        SkeletonQuat inverseParent = parentModel.rotation.inverse();
        SkeletonVec3 localPosition = inverseParent.rotate(position.subtract(parentModel.position));
        SkeletonQuat localRotation = inverseParent.multiply(rotation);
        return new RigidTransform(localPosition, localRotation);
    }
}
