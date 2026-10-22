package dev.nire9734.epicfightposture.compat.humanoid.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;

/**
 * Client-side adapter for a model/render library that can expose humanoid rigs to EFPI.
 *
 * <p>Backends only own source-model inspection/conversion and renderer-specific output.
 * Humanoid classification, Epic Fight patch registration and target-armature creation
 * remain centralized in {@link HumanoidBackendRegistry}.</p>
 */
public interface HumanoidRenderBackend {
    String id();

    Inspection inspect(EntityType<?> entityType, EntityRenderer<?> renderer);

    void addPatchedRenderer(PatchedRenderersEvent.Add event, PreparedHumanoidModel prepared);

    record Inspection(boolean matched, PreparedHumanoidModel prepared) {
        public static Inspection unmatched() {
            return new Inspection(false, null);
        }

        public static Inspection rejected() {
            return new Inspection(true, null);
        }

        public static Inspection supported(PreparedHumanoidModel prepared) {
            if (prepared == null) {
                throw new IllegalArgumentException("prepared model cannot be null");
            }
            return new Inspection(true, prepared);
        }
    }
}
