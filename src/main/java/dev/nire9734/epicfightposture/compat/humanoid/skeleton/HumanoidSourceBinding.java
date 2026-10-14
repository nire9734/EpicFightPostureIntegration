package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

import java.util.Optional;

/**
 * Framework-specific source-node lookup kept outside the portable skeleton descriptor.
 *
 * @param <T> source node type such as GeckoLib GeoBone, Lionfish AdvancedModelBox,
 *            or vanilla ModelPart
 */
public interface HumanoidSourceBinding<T> {
    Optional<T> sourceNode(HumanoidJoint joint);

    default boolean hasSourceNode(HumanoidJoint joint) {
        return sourceNode(joint).isPresent();
    }
}
