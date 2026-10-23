package dev.nire9734.epicfightposture.neoforge.compat.models;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoArmorCacheKey;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NeoForgeGeckoArmorCacheKeyTest {
    @Test
    void equalNamedSourcesRemainDistinctByIdentity() {
        Object first = new String("arm");
        Object second = new String("arm");

        assertEquals(GeckoArmorCacheKey.sourceIdentity(first), GeckoArmorCacheKey.sourceIdentity(first));
        assertNotEquals(GeckoArmorCacheKey.sourceIdentity(first), GeckoArmorCacheKey.sourceIdentity(second));
    }

    @Test
    void visibilityChangesProduceDifferentModelVariants() {
        GeckoArmorCacheKey.BoneState visible = GeckoArmorCacheKey.boneState(
                false, false,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F
        );
        GeckoArmorCacheKey.BoneState hidden = GeckoArmorCacheKey.boneState(
                true, false,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F
        );

        assertNotEquals(
                GeckoArmorCacheKey.modelState(List.of(visible)),
                GeckoArmorCacheKey.modelState(List.of(hidden))
        );
    }

    @Test
    void transformChangesProduceDifferentModelVariants() {
        GeckoArmorCacheKey.BoneState first = GeckoArmorCacheKey.boneState(
                false, false,
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F
        );
        GeckoArmorCacheKey.BoneState moved = GeckoArmorCacheKey.boneState(
                false, false,
                0.25F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, 1.0F,
                0.0F, 0.0F, 0.0F
        );

        assertNotEquals(
                GeckoArmorCacheKey.modelState(List.of(first)),
                GeckoArmorCacheKey.modelState(List.of(moved))
        );
    }
}
