package dev.nire9734.epicfightposture.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerVersionNoticeTest {
    @AfterEach
    void clear() {
        ServerVersionNotice.clear();
    }

    @Test
    void differentVersionIsReportedOnce() {
        ServerVersionNotice.receive("3.3.1");
        assertEquals("3.3.1", ServerVersionNotice.takeMismatch("3.3.0"));
        assertNull(ServerVersionNotice.takeMismatch("3.3.0"), "only once per login");
    }

    @Test
    void newerClientIsAlsoReported() {
        ServerVersionNotice.receive("3.3.0");
        assertEquals("3.3.0", ServerVersionNotice.takeMismatch("3.3.1"));
    }

    @Test
    void sameVersionIsSilent() {
        ServerVersionNotice.receive("3.3.0");
        assertNull(ServerVersionNotice.takeMismatch("3.3.0"));
    }

    @Test
    void nothingReceivedIsSilent() {
        assertNull(ServerVersionNotice.takeMismatch("3.3.0"));
    }

    @Test
    void logoutDiscardsAnUnshownNotice() {
        ServerVersionNotice.receive("3.3.1");
        ServerVersionNotice.clear();
        assertNull(ServerVersionNotice.takeMismatch("3.3.0"));
    }

    @Test
    void blankOrMissingVersionsAreIgnored() {
        assertFalse(ServerVersionNotice.differs("", "3.3.0"));
        assertFalse(ServerVersionNotice.differs("3.3.0", " "));
        assertFalse(ServerVersionNotice.differs(null, "3.3.0"));
        assertTrue(ServerVersionNotice.differs("3.3.1", "3.3.0"));
    }
}
