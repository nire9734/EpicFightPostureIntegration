package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.Objects;

/** Cache key for a distinct source model skeleton. */
public record SkeletonKey(String entityTypeId, String modelSignature) {
    public SkeletonKey {
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        Objects.requireNonNull(modelSignature, "modelSignature");
        if (entityTypeId.isBlank()) {
            throw new IllegalArgumentException("entityTypeId must not be blank");
        }
        if (modelSignature.isBlank()) {
            throw new IllegalArgumentException("modelSignature must not be blank");
        }
    }
}
