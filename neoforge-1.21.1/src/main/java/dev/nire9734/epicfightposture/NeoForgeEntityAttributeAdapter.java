package dev.nire9734.epicfightposture;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Minimal Minecraft 1.21.1 bridge to the common posture math.
 *
 * This must remain platform-specific: Minecraft's attribute API changes
 * across versions, while PostureInputs and PostureBalance are kept
 * loader-independent. Used by NeoForge's authoritative server-side combat handler.
 */
final class NeoForgeEntityAttributeAdapter {
    /** Live inputs for maximum Posture; NeoForge always scales by armor. */
    static PostureInputs liveInputs(LivingEntity mob) {
        return PostureInputs.of(
                mob.getMaxHealth(),
                (float) mob.getAttributeValue(Attributes.ARMOR),
                (float) mob.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                PostureTuning.MINIMUM_POSTURE,
                true);
    }

    private NeoForgeEntityAttributeAdapter() {
    }
}
