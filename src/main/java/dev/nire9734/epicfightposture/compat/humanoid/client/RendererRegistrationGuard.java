package dev.nire9734.epicfightposture.compat.humanoid.client;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Registers a renderer without leaving an EFPI-owned patch active if installation
 * fails. Installation may partially mutate Epic Fight's registry before throwing,
 * so rejection must be performed even when rollback is unavailable.
 */
final class RendererRegistrationGuard {
    static boolean install(Runnable installation, Runnable reject,
                           Consumer<Throwable> reportFailure) {
        Objects.requireNonNull(installation, "installation");
        Objects.requireNonNull(reject, "reject");
        Objects.requireNonNull(reportFailure, "reportFailure");
        try {
            installation.run();
            return true;
        } catch (Throwable failure) {
            try {
                reject.run();
            } catch (Throwable rejectionFailure) {
                if (rejectionFailure != failure) {
                    failure.addSuppressed(rejectionFailure);
                }
            }
            reportFailure.accept(failure);
            return false;
        }
    }

    private RendererRegistrationGuard() {
    }
}
