package dev.nire9734.epicfightposture.compat.humanoid;

import java.util.UUID;
import java.util.function.Consumer;

/** Side-safe callback bridge for dedicated-server discovery requests. */
public final class HumanoidDiscoveryClientBridge {
    private static volatile Consumer<UUID> handler = nonce -> { };

    public static void install(Consumer<UUID> clientHandler) {
        handler = clientHandler == null ? nonce -> { } : clientHandler;
    }

    public static void handle(UUID nonce) {
        if (nonce != null) {
            handler.accept(nonce);
        }
    }

    private HumanoidDiscoveryClientBridge() {
    }
}
