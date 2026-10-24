package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PostureExecutionEligibilityTest {
    @Test
    void missingPatchIsNeverAdmitted() {
        PostureExecutionEligibility.Comparison comparison =
                PostureExecutionEligibility.compare(null, false, false, null, true);

        assertFalse(comparison.legacyEligible());
        assertEquals(PostureExecutionEligibility.Status.UNSUPPORTED, comparison.current().status());
        assertEquals(PostureExecutionEligibility.Reason.MISSING_PATCH, comparison.current().reason());
    }

    @Test
    void exclusionsApplyToBothPredicates() {
        PostureExecutionEligibility.Comparison comparison =
                PostureExecutionEligibility.compare(null, false, false,
                        PostureExecutionEligibility.Reason.ENTITY_BLACKLISTED, true);

        assertFalse(comparison.legacyEligible());
        assertEquals(PostureExecutionEligibility.Reason.ENTITY_BLACKLISTED, comparison.current().reason());
    }

    @Test
    void absentArmatureRootFailsConservativeValidation() {
        assertFalse(PostureExecutionEligibility.validJointHierarchy(null, 4));
    }
}
