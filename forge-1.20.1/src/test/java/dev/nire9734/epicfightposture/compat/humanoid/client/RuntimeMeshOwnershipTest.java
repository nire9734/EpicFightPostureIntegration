package dev.nire9734.epicfightposture.compat.humanoid.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMeshOwnershipTest {
    @Test
    void ownedConstructionIsScopedToOperation() {
        assertFalse(RuntimeMeshOwnership.isOwnedConstruction());

        String result = RuntimeMeshOwnership.callOwned(() -> {
            assertTrue(RuntimeMeshOwnership.isOwnedConstruction());
            return "owned";
        });

        assertEquals("owned", result);
        assertFalse(RuntimeMeshOwnership.isOwnedConstruction());
    }

    @Test
    void nestedOwnershipPreservesOuterScope() {
        RuntimeMeshOwnership.callOwned(() -> {
            assertTrue(RuntimeMeshOwnership.isOwnedConstruction());
            RuntimeMeshOwnership.callOwned(() -> {
                assertTrue(RuntimeMeshOwnership.isOwnedConstruction());
                return null;
            });
            assertTrue(RuntimeMeshOwnership.isOwnedConstruction());
            return null;
        });

        assertFalse(RuntimeMeshOwnership.isOwnedConstruction());
    }

    @Test
    void ownershipIsRestoredAfterException() {
        assertThrows(IllegalStateException.class, () ->
                RuntimeMeshOwnership.callOwned(() -> {
                    assertTrue(RuntimeMeshOwnership.isOwnedConstruction());
                    throw new IllegalStateException("test failure");
                }));

        assertFalse(RuntimeMeshOwnership.isOwnedConstruction());
    }
}
