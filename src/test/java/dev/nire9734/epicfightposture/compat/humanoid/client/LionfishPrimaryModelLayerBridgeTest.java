package dev.nire9734.epicfightposture.compat.humanoid.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LionfishPrimaryModelLayerBridgeTest {
    @Test
    void unchangedPrimaryVisibilityCanReusePreparedMesh() {
        DummyPart root = new DummyPart();
        DummyPart arm = new DummyPart();
        root.childModels.add(arm);
        DummyModel model = new DummyModel(root);

        LionfishPrimaryModelLayerBridge.VisibilitySnapshot snapshot =
                LionfishPrimaryModelLayerBridge.captureVisibility(model);

        assertNotNull(snapshot);
        assertTrue(LionfishPrimaryModelLayerBridge.visibilityMatches(snapshot));
    }

    @Test
    void layerVisibilityChangeFallsBackToSourcePartReplay() {
        DummyPart root = new DummyPart();
        DummyPart armDecoration = new DummyPart();
        root.childModels.add(armDecoration);
        DummyModel model = new DummyModel(root);

        LionfishPrimaryModelLayerBridge.VisibilitySnapshot snapshot =
                LionfishPrimaryModelLayerBridge.captureVisibility(model);
        assertNotNull(snapshot);

        armDecoration.showModel = false;
        assertFalse(LionfishPrimaryModelLayerBridge.visibilityMatches(snapshot));
    }

    @Test
    void hiddenStateChangeAlsoFallsBackToSourcePartReplay() {
        DummyPart root = new DummyPart();
        DummyModel model = new DummyModel(root);

        LionfishPrimaryModelLayerBridge.VisibilitySnapshot snapshot =
                LionfishPrimaryModelLayerBridge.captureVisibility(model);
        assertNotNull(snapshot);

        root.hidden = true;
        assertFalse(LionfishPrimaryModelLayerBridge.visibilityMatches(snapshot));
    }

    private static final class DummyModel {
        private final List<DummyPart> roots;

        private DummyModel(DummyPart root) {
            this.roots = List.of(root);
        }

        @SuppressWarnings("unused")
        public Iterable<DummyPart> parts() {
            return roots;
        }
    }

    private static final class DummyPart {
        @SuppressWarnings("unused")
        public boolean showModel = true;
        @SuppressWarnings("unused")
        public final List<DummyPart> childModels = new ArrayList<>();
        private boolean hidden;

        @SuppressWarnings("unused")
        public boolean isHidden() {
            return hidden;
        }
    }
}
