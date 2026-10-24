package dev.nire9734.epicfightposture.compat.humanoid;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RendererDiscoverySessionsTest {
    @Test
    void noncesBelongToOneOperatorAndAreSingleUse() {
        RendererDiscoverySessions sessions = new RendererDiscoverySessions();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID firstNonce = sessions.issue(first);
        UUID secondNonce = sessions.issue(second);

        assertFalse(sessions.consume(second, firstNonce));
        assertTrue(sessions.consume(first, firstNonce));
        assertFalse(sessions.consume(first, firstNonce));
        assertFalse(sessions.consume(second, secondNonce));
    }

    @Test
    void reloadCooldownIsPerOperatorAndClearsOnLogout() {
        RendererDiscoverySessions sessions = new RendererDiscoverySessions();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID nonce = sessions.issueAfterReload(first, 100);
        assertNotNull(nonce);
        assertNull(sessions.issueAfterReload(first, 101));
        assertNotNull(sessions.issueAfterReload(second, 101));
        assertNotNull(sessions.issueAfterReload(first,
                100 + RendererDiscoveryShared.RELOAD_REQUEST_COOLDOWN_TICKS));

        // A normal login/grant is never throttled by earlier resource reloads.
        UUID loginNonce = sessions.issue(first);
        assertTrue(sessions.consume(first, loginNonce));
        sessions.clear(first);
        assertNotNull(sessions.issueAfterReload(first, 105));
        assertFalse(sessions.consume(first, nonce));
    }
}
