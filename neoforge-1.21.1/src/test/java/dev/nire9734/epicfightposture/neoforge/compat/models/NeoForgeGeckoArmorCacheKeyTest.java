package dev.nire9734.epicfightposture.neoforge.compat.models;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NeoForgeGeckoArmorCacheKeyTest {
    @Test
    void equalNamedSourcesRemainDistinctByIdentity() {
        Object first = new String("arm");
        Object second = new String("arm");

        assertEquals(NeoForgeGeckoArmorCacheKey.sourceIdentity(first), NeoForgeGeckoArmorCacheKey.sourceIdentity(first));
        assertNotEquals(NeoForgeGeckoArmorCacheKey.sourceIdentity(first), NeoForgeGeckoArmorCacheKey.sourceIdentity(second));
    }

    @Test
    void visibilityChangesProduceDifferentModelVariants() {
        NeoForgeGeckoArmorCacheKey.BoneState visible = NeoForgeGeckoArmorCacheKey.boneState(
                false, false,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F
        );
        NeoForgeGeckoArmorCacheKey.BoneState hidden = NeoForgeGeckoArmorCacheKey.boneState(
                true, false,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F
        );

        assertNotEquals(
                NeoForgeGeckoArmorCacheKey.modelState(List.of(visible)),
                NeoForgeGeckoArmorCacheKey.modelState(List.of(hidden))
        );
    }

    @Test
    void transformChangesProduceDifferentModelVariants() {
        NeoForgeGeckoArmorCacheKey.BoneState first = NeoForgeGeckoArmorCacheKey.boneState(
                false, false,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F
        );
        NeoForgeGeckoArmorCacheKey.BoneState moved = NeoForgeGeckoArmorCacheKey.boneState(
                false, false,
                0.25F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F
        );

        assertNotEquals(
                NeoForgeGeckoArmorCacheKey.modelState(List.of(first)),
                NeoForgeGeckoArmorCacheKey.modelState(List.of(moved))
        );
    }
}
