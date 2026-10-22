package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;

import java.util.Optional;

/**
 * Framework-neutral bind-geometry helpers shared by humanoid analysis and
 * skeleton resolution.
 *
 * <p>Framework adapters should only describe source nodes and model-space
 * geometry. This class owns the common rules for ignoring attachment branches,
 * aggregating deforming geometry, and recovering a stable distal joint position
 * from that geometry.</p>
 */
public final class HumanoidGeometryInference {
    private static final float EPSILON = 1.0E-6F;

    public static Optional<BodyBounds> ownOrSubtreeBounds(SourceRigSnapshot snapshot, String nodeId) {
        if (isAttachmentBranch(snapshot, nodeId)) {
            return Optional.empty();
        }
        Optional<BodyBounds> own = snapshot.node(nodeId).geometryBounds();
        return own.isPresent() ? own : subtreeBounds(snapshot, nodeId);
    }

    public static Optional<BodyBounds> subtreeBounds(SourceRigSnapshot snapshot, String rootId) {
        if (isAttachmentBranch(snapshot, rootId)) {
            return Optional.empty();
        }
        BoundsAccumulator bounds = new BoundsAccumulator();
        collectBounds(snapshot, rootId, bounds, true);
        return bounds.toBounds();
    }

    public static Optional<SkeletonVec3> inferDistalCenter(
            SourceRigSnapshot snapshot,
            String branchRootId,
            SkeletonVec3 anchor) {
        return subtreeBounds(snapshot, branchRootId)
                .map(bounds -> distalCenter(bounds, anchor));
    }

    /**
     * Returns the center of the distal face on the bounds' dominant span axis.
     * This avoids the lateral X/Z (or Y/Z) bias caused by choosing a farthest
     * bounding-box corner while retaining a deterministic terminal estimate for
     * one-bone limbs.
     */
    static SkeletonVec3 distalCenter(BodyBounds bounds, SkeletonVec3 anchor) {
        float[] spans = {bounds.width(), bounds.height(), bounds.depth()};
        SkeletonVec3[] candidates = {
                distalFaceCenterX(bounds, anchor),
                distalFaceCenterY(bounds, anchor),
                distalFaceCenterZ(bounds, anchor)
        };

        int axis = 0;
        for (int i = 1; i < spans.length; i++) {
            if (spans[i] > spans[axis] + EPSILON
                    || (Math.abs(spans[i] - spans[axis]) <= EPSILON
                    && candidates[i].subtract(anchor).lengthSquared()
                    > candidates[axis].subtract(anchor).lengthSquared())) {
                axis = i;
            }
        }
        return candidates[axis];
    }

    static boolean isAttachmentBranch(SourceRigSnapshot snapshot, String nodeId) {
        SourceNodeSnapshot cursor = snapshot.node(nodeId);
        while (true) {
            if (SourceNodeHeuristics.likelyAttachment(cursor.name())) {
                return true;
            }
            if (cursor.parentId().isEmpty()) {
                return false;
            }
            cursor = snapshot.node(cursor.parentId().orElseThrow());
        }
    }

    private static void collectBounds(
            SourceRigSnapshot snapshot,
            String nodeId,
            BoundsAccumulator bounds,
            boolean root) {
        SourceNodeSnapshot node = snapshot.node(nodeId);
        if (!root && SourceNodeHeuristics.likelyAttachment(node.name())) {
            return;
        }
        node.geometryBounds().ifPresent(bounds::include);
        for (SourceNodeSnapshot child : snapshot.childrenOf(nodeId)) {
            collectBounds(snapshot, child.id(), bounds, false);
        }
    }

    private static SkeletonVec3 distalFaceCenterX(BodyBounds bounds, SkeletonVec3 anchor) {
        SkeletonVec3 center = bounds.center();
        float x = Math.abs(bounds.min().x() - anchor.x()) >= Math.abs(bounds.max().x() - anchor.x())
                ? bounds.min().x()
                : bounds.max().x();
        return new SkeletonVec3(x, center.y(), center.z());
    }

    private static SkeletonVec3 distalFaceCenterY(BodyBounds bounds, SkeletonVec3 anchor) {
        SkeletonVec3 center = bounds.center();
        float y = Math.abs(bounds.min().y() - anchor.y()) >= Math.abs(bounds.max().y() - anchor.y())
                ? bounds.min().y()
                : bounds.max().y();
        return new SkeletonVec3(center.x(), y, center.z());
    }

    private static SkeletonVec3 distalFaceCenterZ(BodyBounds bounds, SkeletonVec3 anchor) {
        SkeletonVec3 center = bounds.center();
        float z = Math.abs(bounds.min().z() - anchor.z()) >= Math.abs(bounds.max().z() - anchor.z())
                ? bounds.min().z()
                : bounds.max().z();
        return new SkeletonVec3(center.x(), center.y(), z);
    }

    private static final class BoundsAccumulator {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private boolean any;

        void include(BodyBounds bounds) {
            any = true;
            minX = Math.min(minX, bounds.min().x());
            minY = Math.min(minY, bounds.min().y());
            minZ = Math.min(minZ, bounds.min().z());
            maxX = Math.max(maxX, bounds.max().x());
            maxY = Math.max(maxY, bounds.max().y());
            maxZ = Math.max(maxZ, bounds.max().z());
        }

        Optional<BodyBounds> toBounds() {
            if (!any) {
                return Optional.empty();
            }
            return Optional.of(new BodyBounds(
                    new SkeletonVec3(minX, minY, minZ),
                    new SkeletonVec3(maxX, maxY, maxZ)
            ));
        }
    }

    private HumanoidGeometryInference() {
    }
}
