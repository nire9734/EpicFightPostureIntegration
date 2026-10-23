package dev.nire9734.epicfightposture;

import net.minecraft.world.damagesource.DamageSource;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;


/**
 * NeoForge adapter for Forge 3.0.2's direct-hit Impact policy.
 *
 * <p>Use the Impact carried by the actual Epic Fight damage source, not the
 * player's currently selected hand (which can differ for a skill attack).
 * Ordinary player hits retain Forge's bounded damage-derived fallback.</p>
 */
final class NeoForgeCompatibilityDamage {

    static float calculateBaseImpact(DamageSource source, float damageAmount) {
        if (source == null || !Float.isFinite(damageAmount) || damageAmount <= 0.0F) {
            return 0.0F;
        }

        if (source instanceof EpicFightDamageSource epicFightSource) {
            float value = epicFightSource.calculateImpact();
            return CompatibilityImpactPolicy.epicFightImpact(value);
        }

        return CompatibilityImpactPolicy.isFallbackEligible(source.getMsgId())
                ? CompatibilityImpactPolicy.fallbackImpact(damageAmount) : 0.0F;
    }

    static float fallbackImpact(float damageAmount) {
        return CompatibilityImpactPolicy.fallbackImpact(damageAmount);
    }

    static boolean isFallbackEligible(String id) {
        return CompatibilityImpactPolicy.isFallbackEligible(id);
    }

    private NeoForgeCompatibilityDamage() {}
}
