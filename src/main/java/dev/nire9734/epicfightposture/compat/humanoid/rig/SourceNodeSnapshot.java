package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;

import java.util.Objects;
import java.util.Optional;

/**
 * Framework-neutral snapshot of one source model node in authored bind pose.
 *
 * <p>The node id must be unique within a {@link SourceRigSnapshot}. Names do not
 * need to be unique. Geometry bounds are model-space bounds after authored bind
 * transforms and scale have been applied.</p>
 */
public record SourceNodeSnapshot(
        String id,
        String name,
        Optional<String> parentId,
        RigidTransform bindModelTransform,
        Optional<BodyBounds> geometryBounds,
        boolean renderable
) {
    public SourceNodeSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(bindModelTransform, "bindModelTransform");
        Objects.requireNonNull(geometryBounds, "geometryBounds");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Source node id must not be blank");
        }
        if (parentId.filter(id::equals).isPresent()) {
            throw new IllegalArgumentException("Source node cannot parent itself: " + id);
        }
    }

    public SourceNodeSnapshot(
            String id,
            String name,
            String parentId,
            RigidTransform bindModelTransform,
            BodyBounds geometryBounds,
            boolean renderable) {
        this(
                id,
                name == null ? "" : name,
                Optional.ofNullable(parentId),
                bindModelTransform,
                Optional.ofNullable(geometryBounds),
                renderable
        );
    }
}
