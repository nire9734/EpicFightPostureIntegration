package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Library-independent raw bind-rig snapshot produced by a framework adapter.
 *
 * <p>This is intentionally less semantic than HumanoidSkeletonDescriptor. Gecko,
 * Lionfish, Citadel, and vanilla adapters should stop at this representation;
 * humanoid interpretation happens in a separate analyzer/resolver layer.</p>
 */
public final class SourceRigSnapshot {
    private final SkeletonKey key;
    private final Map<String, SourceNodeSnapshot> nodes;
    private final BodyBounds bounds;
    private final boolean hadNonUniformBindScale;

    public SourceRigSnapshot(
            SkeletonKey key,
            Map<String, SourceNodeSnapshot> nodes,
            BodyBounds bounds,
            boolean hadNonUniformBindScale) {
        this.key = Objects.requireNonNull(key, "key");
        Objects.requireNonNull(nodes, "nodes");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.hadNonUniformBindScale = hadNonUniformBindScale;

        LinkedHashMap<String, SourceNodeSnapshot> copy = new LinkedHashMap<>();
        nodes.forEach((id, node) -> {
            Objects.requireNonNull(id, "source node map key");
            Objects.requireNonNull(node, "source node");
            if (!id.equals(node.id())) {
                throw new IllegalArgumentException("Source node map key does not match node id: " + id);
            }
            copy.put(id, node);
        });
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Source rig snapshot must contain at least one node");
        }

        for (SourceNodeSnapshot node : copy.values()) {
            node.parentId().ifPresent(parentId -> {
                if (!copy.containsKey(parentId)) {
                    throw new IllegalArgumentException(
                            "Source node " + node.id() + " references missing parent " + parentId);
                }
            });
        }
        validateAcyclic(copy);
        this.nodes = Map.copyOf(copy);
    }

    private static void validateAcyclic(Map<String, SourceNodeSnapshot> nodes) {
        for (String start : nodes.keySet()) {
            Set<String> visited = new HashSet<>();
            String cursor = start;
            while (cursor != null) {
                if (!visited.add(cursor)) {
                    throw new IllegalArgumentException("Cycle in source rig at node " + cursor);
                }
                cursor = nodes.get(cursor).parentId().orElse(null);
            }
        }
    }

    public SkeletonKey key() {
        return key;
    }

    public Map<String, SourceNodeSnapshot> nodes() {
        return nodes;
    }

    public SourceNodeSnapshot node(String id) {
        SourceNodeSnapshot node = nodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("Unknown source node " + id);
        }
        return node;
    }

    public BodyBounds bounds() {
        return bounds;
    }

    public boolean hadNonUniformBindScale() {
        return hadNonUniformBindScale;
    }

    public List<SourceNodeSnapshot> roots() {
        return nodes.values().stream()
                .filter(node -> node.parentId().isEmpty())
                .toList();
    }

    public List<SourceNodeSnapshot> childrenOf(String parentId) {
        List<SourceNodeSnapshot> result = new ArrayList<>();
        for (SourceNodeSnapshot node : nodes.values()) {
            if (node.parentId().filter(parentId::equals).isPresent()) {
                result.add(node);
            }
        }
        return List.copyOf(result);
    }

    public int depth(String nodeId) {
        int depth = 0;
        SourceNodeSnapshot cursor = node(nodeId);
        while (cursor.parentId().isPresent()) {
            depth++;
            cursor = node(cursor.parentId().orElseThrow());
        }
        return depth;
    }

    public boolean isAncestor(String ancestorId, String descendantId) {
        SourceNodeSnapshot cursor = node(descendantId);
        while (cursor.parentId().isPresent()) {
            String parentId = cursor.parentId().orElseThrow();
            if (ancestorId.equals(parentId)) {
                return true;
            }
            cursor = node(parentId);
        }
        return false;
    }
}
