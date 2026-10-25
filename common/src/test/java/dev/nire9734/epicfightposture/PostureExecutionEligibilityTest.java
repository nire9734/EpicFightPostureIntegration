package dev.nire9734.epicfightposture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PostureExecutionEligibilityTest {
    @Test
    void missingPatchIsNeverAdmitted() {
        PostureExecutionEligibility.Result result =
                PostureExecutionEligibility.evaluate(null, false, false, null, true);

        assertEquals(PostureExecutionEligibility.Status.UNSUPPORTED, result.status());
        assertEquals(PostureExecutionEligibility.Reason.MISSING_PATCH, result.reason());
    }

    @Test
    void exclusionsApplyBeforeAnimationPreflight() {
        PostureExecutionEligibility.Result result =
                PostureExecutionEligibility.evaluate(null, false, false,
                        PostureExecutionEligibility.Reason.ENTITY_BLACKLISTED, true);

        assertEquals(PostureExecutionEligibility.Reason.ENTITY_BLACKLISTED, result.reason());
    }

    @Test
    void absentArmatureRootFailsConservativeValidation() {
        assertFalse(PostureExecutionEligibility.validJointHierarchy(null, 4));
    }
}
