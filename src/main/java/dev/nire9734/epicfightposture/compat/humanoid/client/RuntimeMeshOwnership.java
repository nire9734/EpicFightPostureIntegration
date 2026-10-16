package dev.nire9734.epicfightposture.compat.humanoid.client;

import it.unimi.dsi.fastutil.ints.IntList;
import yesman.epicfight.api.client.model.MeshPartDefinition;
import yesman.epicfight.api.client.model.SingleGroupVertexBuilder;
import yesman.epicfight.api.client.model.SkinnedMesh;

import java.util.List;
import java.util.Map;

/**
 * Explicit construction ownership for SkinnedMesh instances baked by EFPI.
 *
 * <p>The scope is intentionally opened only around EFPI's calls to
 * {@link SingleGroupVertexBuilder#loadVertexInformation}. Epic Fight checks
 * compute-shader support from inside the SkinnedMesh constructor before it can
 * enqueue an off-thread render call, so the ownership signal is available at
 * the earliest useful point and does not depend on the Java call stack.</p>
 */
public final class RuntimeMeshOwnership {
    private static final ThreadLocal<Integer> CONSTRUCTION_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    public static SkinnedMesh buildOwned(
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) {
        int previousDepth = CONSTRUCTION_DEPTH.get();
        CONSTRUCTION_DEPTH.set(previousDepth + 1);
        try {
            return SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
        } finally {
            if (previousDepth == 0) {
                CONSTRUCTION_DEPTH.remove();
            } else {
                CONSTRUCTION_DEPTH.set(previousDepth);
            }
        }
    }

    public static boolean isOwnedConstruction() {
        return CONSTRUCTION_DEPTH.get() > 0;
    }

    private RuntimeMeshOwnership() {
    }
}
