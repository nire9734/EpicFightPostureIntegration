package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticPatchSafetyTest {
    @Test
    void ordinaryLivingEntityIsEligible() {
        assertTrue(AutomaticPatchSafety.canAttach(true, () -> false));
    }

    @Test
    void multipartParentIsExcludedEvenWhenLiving() {
        assertFalse(AutomaticPatchSafety.canAttach(true, () -> true));
    }

    @Test
    void nonLivingPartWithParentEntityTypeIsExcluded() {
        assertFalse(AutomaticPatchSafety.canAttach(false, () -> false));
        assertFalse(AutomaticPatchSafety.canAttach(false, () -> true));
    }

    @Test
    void neverCallsMultipartOverrideOnNonLivingEntity() {
        assertFalse(AutomaticPatchSafety.canAttach(false,
                () -> { throw new AssertionError("non-living entity should not be inspected"); }));
    }

    @Test
    void failsClosedWhenMultipartProbeRunsBeforeSubclassInitialization() {
        assertFalse(AutomaticPatchSafety.canAttach(true, () -> {
            throw new NullPointerException("subclass field not initialized yet");
        }));
        assertFalse(AutomaticPatchSafety.canAttach(true, () -> {
            throw new NoClassDefFoundError("optional mod class unavailable");
        }));
        assertFalse(AutomaticPatchSafety.canAttach(true, null));
    }

    @Test
    void forgeProviderDoesNotConstructPatchForMultipartOrNonLivingChildren() {
        AtomicInteger creations = new AtomicInteger();
        Supplier<Object> factory = () -> {
            creations.incrementAndGet();
            return new Object();
        };
        assertNull(AutomaticPatchSafety.guardedSupplier(false, () -> false, factory).get());
        assertNull(AutomaticPatchSafety.guardedSupplier(true, () -> true, factory).get());
        assertNull(AutomaticPatchSafety.guardedSupplier(true,
                () -> { throw new IllegalStateException("part state not ready"); }, factory).get());
        assertEquals(0, creations.get());
        AutomaticPatchSafety.guardedSupplier(true, () -> false, factory).get();
        assertEquals(1, creations.get());
    }
}
