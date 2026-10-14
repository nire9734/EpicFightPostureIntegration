package dev.nire9734.epicfightposture.compat.humanoid.retarget;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanoidSkinWeightResolverTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void centeredBendPreservesPreviousEightPercentTransition() {
        float width = HumanoidSkinWeightResolver.transitionHalfWidth(
                new SkeletonVec3(0.0F, 0.0F, 0.0F),
                new SkeletonVec3(0.0F, 1.0F, 0.0F),
                new SkeletonVec3(0.0F, 2.0F, 0.0F)
        );
        assertEquals(0.08F, width, EPSILON);
    }

    @Test
    void transitionWidthIsScaleInvariant() {
        float normal = HumanoidSkinWeightResolver.transitionHalfWidth(
                new SkeletonVec3(0.0F, 0.0F, 0.0F),
                new SkeletonVec3(0.0F, 1.0F, 0.0F),
                new SkeletonVec3(0.0F, 2.0F, 0.0F)
        );
        float large = HumanoidSkinWeightResolver.transitionHalfWidth(
                new SkeletonVec3(0.0F, 0.0F, 0.0F),
                new SkeletonVec3(0.0F, 10.0F, 0.0F),
                new SkeletonVec3(0.0F, 20.0F, 0.0F)
        );
        assertEquals(normal, large, EPSILON);
    }

    @Test
    void unevenLimbSegmentsUseNarrowerTransition() {
        float centered = HumanoidSkinWeightResolver.transitionHalfWidth(
                new SkeletonVec3(0.0F, 0.0F, 0.0F),
                new SkeletonVec3(0.0F, 1.0F, 0.0F),
                new SkeletonVec3(0.0F, 2.0F, 0.0F)
        );
        float uneven = HumanoidSkinWeightResolver.transitionHalfWidth(
                new SkeletonVec3(0.0F, 0.0F, 0.0F),
                new SkeletonVec3(0.0F, 1.0F, 0.0F),
                new SkeletonVec3(0.0F, 4.0F, 0.0F)
        );
        assertTrue(uneven < centered);
        assertEquals(0.04F, uneven, EPSILON);
    }
}
