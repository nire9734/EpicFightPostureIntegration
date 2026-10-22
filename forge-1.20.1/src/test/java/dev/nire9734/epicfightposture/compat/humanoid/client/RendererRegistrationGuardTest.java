package dev.nire9734.epicfightposture.compat.humanoid.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererRegistrationGuardTest {
    @Test
    void successfulInstallationDoesNotReject() {
        AtomicBoolean installed = new AtomicBoolean();
        AtomicBoolean rejected = new AtomicBoolean();
        boolean result = RendererRegistrationGuard.install(
                () -> installed.set(true),
                () -> rejected.set(true),
                ignored -> { throw new AssertionError("must not report success"); });
        assertTrue(result);
        assertTrue(installed.get());
        assertFalse(rejected.get());
    }

    @Test
    void partialInstallationFailureRejectsAndReportsOriginalFailure() {
        AtomicBoolean rejected = new AtomicBoolean();
        AtomicReference<Throwable> reported = new AtomicReference<>();
        IllegalStateException failure = new IllegalStateException("registration failed");
        boolean result = RendererRegistrationGuard.install(
                () -> { throw failure; },
                () -> rejected.set(true),
                reported::set);
        assertFalse(result);
        assertTrue(rejected.get());
        assertSame(failure, reported.get());
    }

    @Test
    void rejectionFailureIsSuppressedWithoutMaskingRegistrationFailure() {
        IllegalStateException failure = new IllegalStateException("registration failed");
        RuntimeException rejectionFailure = new RuntimeException("rejection failed");
        AtomicReference<Throwable> reported = new AtomicReference<>();
        assertFalse(RendererRegistrationGuard.install(
                () -> { throw failure; },
                () -> { throw rejectionFailure; },
                reported::set));
        assertSame(failure, reported.get());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(rejectionFailure, failure.getSuppressed()[0]);
    }
}
