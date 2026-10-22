package dev.nire9734.epicfightposture.neoforge.compat.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeoForgeLionfishAdmissionTest {
    @Test void lowConfidenceRigIsAcceptedWhenForgeResolverAndMeshSucceed() {
        assertTrue(NeoForgeLionfishAdmission.accepts(true, true, true, 0.20F));
        assertTrue(NeoForgeLionfishAdmission.accepts(true, true, true, 0.74F));
        assertTrue(NeoForgeLionfishAdmission.accepts(true, true, true, 1.00F));
    }

    @Test void incompleteOrRejectedRigRemainsUnsupported() {
        assertFalse(NeoForgeLionfishAdmission.accepts(false, true, true, 0.95F));
        assertFalse(NeoForgeLionfishAdmission.accepts(true, false, true, 0.95F));
        assertFalse(NeoForgeLionfishAdmission.accepts(true, true, false, 0.95F));
    }

    @Test void invalidRigQualityIsRejected() {
        assertFalse(NeoForgeLionfishAdmission.accepts(true, true, true, Float.NaN));
        assertFalse(NeoForgeLionfishAdmission.accepts(true, true, true, Float.POSITIVE_INFINITY));
        assertFalse(NeoForgeLionfishAdmission.accepts(true, true, true, -0.01F));
    }
}
