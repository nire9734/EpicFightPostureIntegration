package dev.nire9734.epicfightposture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crash barrier for event handlers and mixin hooks that run inside Minecraft,
 * Epic Fight or Combat Evolution code. Posture is an add-on: if one of its hooks
 * fails, the game must continue without it rather than crash the server tick or
 * the combat event. Each hook logs its first failure only; fatal JVM errors are
 * never swallowed.
 *
 * Plain Java with SLF4J, so it is usable on both sides and in tests.
 */
public final class HookGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger("Epic Fight Posture Integration");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    public static void run(String hook, Runnable body) {
        try {
            body.run();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            report(hook, failure);
        }
    }

    /** True the first time a hook fails, so tests can observe the log-once rule. */
    static boolean report(String hook, Throwable failure) {
        if (!REPORTED.add(hook)) return false;
        LOGGER.error("Posture hook '{}' failed and was skipped; further failures of this hook are not logged",
                hook, failure);
        return true;
    }

    static void resetForTests() {
        REPORTED.clear();
    }

    private HookGuard() {
    }
}
