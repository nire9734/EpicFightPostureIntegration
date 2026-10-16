package dev.nire9734.epicfightposture.compat.humanoid.client;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Explicit construction ownership for runtime meshes baked by EFPI.
 *
 * <p>Epic Fight checks compute-shader support from inside the SkinnedMesh
 * constructor before it can enqueue an off-thread render call. EFPI opens this
 * scope directly around its own mesh-bake operations so the ownership signal is
 * available at the earliest useful point without inspecting the Java call stack
 * or patching EFPI's own classes with Mixins.</p>
 */
public final class RuntimeMeshOwnership {
    private static final ThreadLocal<Integer> CONSTRUCTION_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    public static <T> T callOwned(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        int previousDepth = CONSTRUCTION_DEPTH.get();
        CONSTRUCTION_DEPTH.set(previousDepth + 1);
        try {
            return operation.get();
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
