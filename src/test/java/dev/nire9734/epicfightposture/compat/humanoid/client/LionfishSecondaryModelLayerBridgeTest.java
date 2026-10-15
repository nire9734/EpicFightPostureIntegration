package dev.nire9734.epicfightposture.compat.humanoid.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LionfishSecondaryModelLayerBridgeTest {
    @Test
    void unsupportedSecondaryConversionFallsBackToOriginalLayerRender() {
        assertFalse(LionfishSecondaryModelLayerBridge.shouldReplace(
                LionfishSecondaryModelLayerBridge.CachedSecondary.unsupported()
        ));
    }
}
