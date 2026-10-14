package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Cache key for a distinct source model skeleton. */
public record SkeletonKey(ResourceLocation entityType, String modelSignature) {
    public SkeletonKey {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(modelSignature, "modelSignature");
        if (modelSignature.isBlank()) {
            throw new IllegalArgumentException("modelSignature must not be blank");
        }
    }
}
