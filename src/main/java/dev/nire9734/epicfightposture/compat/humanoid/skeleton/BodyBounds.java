package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.Objects;

/** Model-space bounds of the source model after authored bind transforms are applied. */
public record BodyBounds(SkeletonVec3 min, SkeletonVec3 max) {
    public BodyBounds {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("Body bounds min must not exceed max");
        }
    }

    public float width() {
        return max.x() - min.x();
    }

    public float height() {
        return max.y() - min.y();
    }

    public float depth() {
        return max.z() - min.z();
    }

    public SkeletonVec3 center() {
        return SkeletonVec3.midpoint(min, max);
    }
}
