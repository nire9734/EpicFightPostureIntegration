package dev.nire9734.epicfightposture.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPostureStoreTest {
    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @AfterEach
    void clear() {
        ClientPostureStore.clear();
    }

    @Test
    void storesValidSnapshots() {
        ClientPostureStore.update(7, MOB, 2.0F, 4.0F, false);
        ClientPostureStore.Entry entry = ClientPostureStore.get(7, MOB);
        assertNotNull(entry);
        assertEquals(0.5F, entry.percent(), 1.0E-6F);
    }

    @Test
    void malformedSnapshotsAreDroppedWithoutReplacingTheLastValidOne() {
        ClientPostureStore.update(7, MOB, 2.0F, 4.0F, false);

        ClientPostureStore.update(7, MOB, 1.0F, Float.NaN, false);
        ClientPostureStore.update(7, MOB, Float.NaN, 4.0F, false);
        ClientPostureStore.update(7, MOB, 1.0F, Float.POSITIVE_INFINITY, false);
        ClientPostureStore.update(7, MOB, 1.0F, 0.0F, false);
        ClientPostureStore.update(7, null, 1.0F, 4.0F, false);

        ClientPostureStore.Entry entry = ClientPostureStore.get(7, MOB);
        assertNotNull(entry);
        assertEquals(2.0F, entry.current);
        assertEquals(4.0F, entry.max);
    }

    @Test
    void malformedFirstSnapshotCreatesNoEntry() {
        ClientPostureStore.update(9, MOB, 1.0F, Float.NaN, true);
        assertNull(ClientPostureStore.get(9, MOB));
    }

    @Test
    void validation() {
        assertTrue(ClientPostureStore.isValid(MOB, 0.0F, 0.1F));
        assertFalse(ClientPostureStore.isValid(MOB, 0.0F, -1.0F));
        assertFalse(ClientPostureStore.isValid(MOB, Float.NEGATIVE_INFINITY, 1.0F));
    }
}
