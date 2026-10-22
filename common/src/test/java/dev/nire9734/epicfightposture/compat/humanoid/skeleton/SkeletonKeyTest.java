package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkeletonKeyTest {
    @Test
    void storesPlatformNeutralEntityIdentifier() {
        SkeletonKey key = new SkeletonKey("example:mob", "example.Model|example:model");

        assertEquals("example:mob", key.entityTypeId());
        assertEquals("example.Model|example:model", key.modelSignature());
    }

    @Test
    void rejectsBlankIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new SkeletonKey("", "model"));
        assertThrows(IllegalArgumentException.class, () -> new SkeletonKey("example:mob", ""));
    }
}
