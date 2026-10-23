package dev.nire9734.epicfightposture.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostureHudGeometryTest {
    @Test void healthAndPostureFillsUseTheSameAtlasCoordinates() {
        PostureHudGeometry.Layout hud = PostureHudGeometry.layout(0.5F, 0.25F, false);
        assertEquals(43, hud.healthFill().u2());
        assertEquals(26, hud.postureFill().u2());
        assertEquals(63, hud.postureFill().v1());
        assertEquals(83, hud.postureFill().v2());
    }

    @Test void brokenPostureAlwaysDrawsFullBrokenColor() {
        PostureHudGeometry.Layout hud = PostureHudGeometry.layout(0.0F, 0.0F, true);
        assertEquals(9, hud.postureFill().u1());
        assertEquals(77, hud.postureFill().u2());
        assertEquals(84, hud.postureFill().v1());
        assertEquals(104, hud.postureFill().v2());
        assertTrue(hud.postureFill().right() > hud.postureFill().left());
    }
}
