package dev.nire9734.epicfightposture.compat.humanoid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Loader-independent per-operator nonce and resource-reload request bookkeeping. */
public final class RendererDiscoverySessions {
    private final Map<UUID, UUID> pending = new HashMap<>();
    private final Map<UUID, Integer> lastReloadTick = new HashMap<>();

    /** Starts or supersedes an authorized operator's pending scan. */
    public synchronized UUID issue(UUID operator) {
        UUID nonce = UUID.randomUUID();
        pending.put(operator, nonce);
        return nonce;
    }

    /** Rate-limits only resource-reload requests; logins and OP grants are never throttled. */
    public synchronized UUID issueAfterReload(UUID operator, int serverTick) {
        Integer last = lastReloadTick.get(operator);
        if (last != null && serverTick >= last
                && serverTick - last < RendererDiscoveryShared.RELOAD_REQUEST_COOLDOWN_TICKS) {
            return null;
        }
        lastReloadTick.put(operator, serverTick);
        return issue(operator);
    }

    /** A nonce can only be consumed once and belongs to exactly one operator. */
    public synchronized boolean consume(UUID operator, UUID nonce) {
        UUID expected = pending.remove(operator);
        return expected != null && expected.equals(nonce);
    }

    public synchronized void clear(UUID operator) {
        pending.remove(operator);
        lastReloadTick.remove(operator);
    }
}
