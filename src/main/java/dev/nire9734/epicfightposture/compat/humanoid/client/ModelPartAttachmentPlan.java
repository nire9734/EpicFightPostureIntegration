package dev.nire9734.epicfightposture.compat.humanoid.client;

import yesman.epicfight.api.client.model.SkinnedMesh;

import java.util.List;
import java.util.Objects;

/**
 * Runtime-renderable attachment geometry kept separate from the static body mesh.
 * Each entry corresponds to one source ModelPart node so source visibility can be
 * evaluated every frame (for example Maledictus bow/mace/halberd switching).
 */
public record ModelPartAttachmentPlan(List<Entry> entries) {
    public ModelPartAttachmentPlan {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public record Entry(List<String> path, SkinnedMesh mesh) {
        public Entry {
            path = path == null ? List.of() : List.copyOf(path);
            Objects.requireNonNull(mesh, "mesh");
        }
    }
}
