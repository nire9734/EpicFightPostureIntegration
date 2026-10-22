package dev.nire9734.epicfightposture.neoforge.client;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NeoForgeBackendSelectionTest {
    @Test void noPreparedBackendRemainsUnsupported() {
        assertNull(NeoForgeBackendSelection.unique(List.of()));
    }

    @Test void exactlyOnePreparedBackendIsEligible() {
        assertEquals("lionfish", NeoForgeBackendSelection.unique(List.of("lionfish")));
        assertEquals("geckolib", NeoForgeBackendSelection.unique(List.of("geckolib")));
        assertEquals("modelpart", NeoForgeBackendSelection.unique(List.of("modelpart")));
    }

    @Test void overlappingBackendsAreNotChosenByOrder() {
        assertNull(NeoForgeBackendSelection.unique(List.of("geckolib", "lionfish")));
        assertNull(NeoForgeBackendSelection.unique(List.of("lionfish", "geckolib")));
        assertNull(NeoForgeBackendSelection.unique(List.of("modelpart", "lionfish")));
    }
}
