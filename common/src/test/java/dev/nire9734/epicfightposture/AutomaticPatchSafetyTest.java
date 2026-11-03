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
        assertTrue(AutomaticPatchSafety.canAttach(true, false, () -> false));
    }

    @Test
    void multipartParentIsExcludedEvenWhenLiving() {
        assertFalse(AutomaticPatchSafety.canAttach(true, false, () -> true));
    }

    @Test
    void nonLivingPartWithParentEntityTypeIsExcluded() {
        assertFalse(AutomaticPatchSafety.canAttach(false, false, () -> false));
        assertFalse(AutomaticPatchSafety.canAttach(false, false, () -> true));
    }

    @Test
    void neverCallsMultipartOverrideOnNonLivingEntity() {
        assertFalse(AutomaticPatchSafety.canAttach(false, false,
                () -> { throw new AssertionError("non-living entity should not be inspected"); }));
    }

    @Test
    void failsClosedWhenMultipartProbeRunsBeforeSubclassInitialization() {
        assertFalse(AutomaticPatchSafety.canAttach(true, false, () -> {
            throw new NullPointerException("subclass field not initialized yet");
        }));
        assertFalse(AutomaticPatchSafety.canAttach(true, false, () -> {
            throw new NoClassDefFoundError("optional mod class unavailable");
        }));
        assertFalse(AutomaticPatchSafety.canAttach(true, false, null));
    }

    static class BaseEntity {
        public boolean isMultipartEntity() { return false; }
        public Object[] getParts() { return null; }
    }

    static class OrdinaryMob extends BaseEntity {}

    static class PrematureMultipartMob extends BaseEntity {
        // During Entity's constructor, an uninitialized field could produce false.
        @Override public boolean isMultipartEntity() { return false; }
    }

    static class PartsOnlyMob extends BaseEntity {
        @Override public Object[] getParts() { return null; }
    }

    static class InheritedMultipartMob extends PrematureMultipartMob {}

    @Test
    void structureVetoesMultipartOverridesEvenIfRuntimeProbeWouldSayFalse() {
        assertFalse(AutomaticPatchSafety.hasCustomMultipartContract(OrdinaryMob.class, BaseEntity.class));
        assertTrue(AutomaticPatchSafety.hasCustomMultipartContract(PrematureMultipartMob.class, BaseEntity.class));
        assertTrue(AutomaticPatchSafety.hasCustomMultipartContract(PartsOnlyMob.class, BaseEntity.class));
        assertTrue(AutomaticPatchSafety.hasCustomMultipartContract(InheritedMultipartMob.class, BaseEntity.class));
        assertTrue(AutomaticPatchSafety.hasCustomMultipartContract(Object.class, BaseEntity.class));
        assertFalse(AutomaticPatchSafety.canAttach(true, true,
                () -> { throw new AssertionError("Structural veto must run before virtual probe"); }));
    }

    @Test
    void forgeProviderDoesNotConstructPatchForMultipartOrNonLivingChildren() {
        AtomicInteger creations = new AtomicInteger();
        Supplier<Object> factory = () -> {
            creations.incrementAndGet();
            return new Object();
        };
        assertNull(AutomaticPatchSafety.guardedSupplier(false, false, () -> false, factory).get());
        assertNull(AutomaticPatchSafety.guardedSupplier(true, false, () -> true, factory).get());
        assertNull(AutomaticPatchSafety.guardedSupplier(true, true, () -> false, factory).get());
        assertNull(AutomaticPatchSafety.guardedSupplier(true, false,
                () -> { throw new IllegalStateException("part state not ready"); }, factory).get());
        assertEquals(0, creations.get());
        AutomaticPatchSafety.guardedSupplier(true, false, () -> false, factory).get();
        assertEquals(1, creations.get());
    }
}
