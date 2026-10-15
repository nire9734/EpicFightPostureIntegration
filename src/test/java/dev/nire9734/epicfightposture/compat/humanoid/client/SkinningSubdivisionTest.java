package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinningSubdivisionTest {
    @Test
    void rigidEdgesRemainSingleQuads() {
        HumanoidSkinWeightResolver.SkinWeights arm =
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.ARM_RIGHT);

        assertFalse(SkinningSubdivision.weightsDiffer(arm, arm));
        assertEquals(1, SkinningSubdivision.segmentCount(24.0F, false));
    }

    @Test
    void deformingOneBoneLimbEdgesSampleEverySourcePixel() {
        HumanoidSkinWeightResolver.SkinWeights arm =
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.ARM_RIGHT);
        HumanoidSkinWeightResolver.SkinWeights hand =
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.HAND_RIGHT);

        assertTrue(SkinningSubdivision.weightsDiffer(arm, hand));
        assertEquals(24, SkinningSubdivision.segmentCount(24.0F, true));
    }

    @Test
    void deeplingStyleMainLimbAndFinShareIntegerSampleBoundaries() {
        int limbSegments = SkinningSubdivision.segmentCount(24.0F, true);
        int finSegments = SkinningSubdivision.segmentCount(12.0F, true);

        // A Deepling-style main arm runs from -1 to 23 while its planar fin runs
        // from 1 to 13. One-pixel tessellation gives both surfaces identical
        // samples at every attachment position instead of only at unrelated ends.
        for (int y = 1; y <= 13; y++) {
            float limbSample = -1.0F + limbSegments * ((y + 1.0F) / 24.0F) / limbSegments * 24.0F;
            float finSample = 1.0F + finSegments * ((y - 1.0F) / 12.0F) / finSegments * 12.0F;
            assertEquals((float) y, limbSample, 1.0E-5F);
            assertEquals((float) y, finSample, 1.0E-5F);
        }
    }

    @Test
    void pathologicalLongEdgesAreCapped() {
        assertEquals(32, SkinningSubdivision.segmentCount(1000.0F, true));
    }
}
