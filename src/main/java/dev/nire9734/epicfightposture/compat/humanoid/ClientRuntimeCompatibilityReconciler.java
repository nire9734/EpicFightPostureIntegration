package dev.nire9734.epicfightposture.compat.humanoid;

import java.util.Objects;
import java.util.Set;

/**
 * Reconciles client-owned patches after a renderer scan. Only EFPI-owned targets
 * may change state; unverified ones must remain inert until a successful rescan.
 */
final class ClientRuntimeCompatibilityReconciler {
    static <T> void reconcile(Set<T> owned, Set<T> verified, Set<T> rejected) {
        Objects.requireNonNull(owned, "owned");
        Objects.requireNonNull(rejected, "rejected");
        Set<T> accepted = verified == null ? Set.of() : verified;
        for (T id : owned) {
            if (accepted.contains(id)) {
                rejected.remove(id);
            } else {
                rejected.add(id);
            }
        }
    }

    private ClientRuntimeCompatibilityReconciler() {
    }
}
