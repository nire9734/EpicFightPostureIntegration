package dev.nire9734.epicfightposture;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Minimal Minecraft 1.21.1 bridge to the common posture math.
 *
 * This must remain platform-specific: Minecraft's attribute API changes
 * across versions, while PostureBalance is kept loader-independent.
 * No gameplay listeners call it until the full NeoForge adapter is ready.
 */
final class NeoForgeEntityAttributeAdapter {
    static float liveEffectiveHealth(LivingEntity mob) {
        float maxHealth = finiteOr(mob.getMaxHealth(), 20.0F);
        float armor = finiteOr((float) mob.getAttributeValue(Attributes.ARMOR), 0.0F);
        float toughness = finiteOr((float) mob.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0.0F);
        return PostureBalance.effectiveHealth(
                maxHealth <= 0.0F ? 20.0F : maxHealth,
                armor, toughness, 8.0F
        );
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private NeoForgeEntityAttributeAdapter() {
    }
}
