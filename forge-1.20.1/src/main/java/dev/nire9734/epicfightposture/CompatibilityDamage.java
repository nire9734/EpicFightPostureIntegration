package dev.nire9734.epicfightposture;

import net.minecraft.world.damagesource.DamageSource;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;

public final class CompatibilityDamage {

    /**
     * Returns raw Impact for eligible direct player damage (not multiplied by HP loss).
     * Explicit zero-Impact Epic Fight attacks stay excluded; non-EF attacks use
     * a bounded damage-derived approximation.
     */
    public static float calculateBaseImpact(DamageSource source, float damageAmount) {
        if (source == null || !Float.isFinite(damageAmount) || damageAmount <= 0.0F) {
            return 0.0F;
        }

        float baseImpact;
        if (source instanceof EpicFightDamageSource epicFightSource) {
            float value = epicFightSource.calculateImpact();
            baseImpact = CompatibilityImpactPolicy.epicFightImpact(value);
        } else {
            if (!CompatibilityImpactPolicy.isFallbackEligible(source.getMsgId())) {
                return 0.0F;
            }
            baseImpact = CompatibilityImpactPolicy.fallbackImpact(damageAmount);
        }

        return baseImpact;
    }

    private CompatibilityDamage() {}
}
