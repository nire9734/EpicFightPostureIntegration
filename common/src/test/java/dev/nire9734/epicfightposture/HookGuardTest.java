package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookGuardTest {
    @AfterEach
    void reset() {
        HookGuard.resetForTests();
    }

    @Test
    void successfulHooksRun() {
        AtomicBoolean ran = new AtomicBoolean();
        HookGuard.run("tick", () -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    void failuresAreContained() {
        assertDoesNotThrow(() -> HookGuard.run("damage", () -> {
            throw new IllegalStateException("broken mob");
        }));
        assertDoesNotThrow(() -> HookGuard.run("damage", () -> {
            throw new NoSuchMethodError("addon API changed");
        }));
    }

    @Test
    void eachHookIsReportedOnlyOnce() {
        assertTrue(HookGuard.report("parry", new RuntimeException()));
        assertFalse(HookGuard.report("parry", new RuntimeException()));
        assertTrue(HookGuard.report("guard", new RuntimeException()));
    }

    @Test
    void fatalJvmErrorsPropagate() {
        assertThrows(OutOfMemoryError.class, () -> HookGuard.run("tick", () -> {
            throw new OutOfMemoryError();
        }));
    }
}
