package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.Objects;

/** Portable semantic skeleton plus the framework-specific nodes from which it was resolved. */
public record HumanoidModelResolution<T>(
        HumanoidSkeletonDescriptor descriptor,
        HumanoidSourceBinding<T> binding
) {
    public HumanoidModelResolution {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(binding, "binding");
    }
}
