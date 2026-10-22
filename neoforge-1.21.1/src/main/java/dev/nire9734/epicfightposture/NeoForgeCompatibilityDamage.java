package dev.nire9734.epicfightposture;

import net.minecraft.world.damagesource.DamageSource;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;

import java.util.Locale;
import java.util.Set;

/**
 * NeoForge adapter for Forge 3.0.2's direct-hit Impact policy.
 *
 * <p>Use the Impact carried by the actual Epic Fight damage source, not the
 * player's currently selected hand (which can differ for a skill attack).
 * Ordinary player hits retain Forge's bounded damage-derived fallback.</p>
 */
final class NeoForgeCompatibilityDamage {
    private static final float FALLBACK_DIVISOR = 4.0F;
    private static final float FALLBACK_MIN_IMPACT = 0.5F;
    private static final float FALLBACK_MAX_IMPACT = 5.0F;

    private static final Set<String> EXCLUDED_DAMAGE_IDS = Set.of(
            "infire", "onfire", "lava", "hotfloor", "inwall", "cramming", "drown", "starve",
            "cactus", "fall", "flyintowall", "outofworld", "outsideborder", "freeze", "stalagmite",
            "sweetberrybush", "dryout", "generickill", "wither", "poison", "bleed"
    );

    static float calculateBaseImpact(DamageSource source, float damageAmount) {
        if (source == null || !Float.isFinite(damageAmount) || damageAmount <= 0.0F) {
            return 0.0F;
        }

        if (source instanceof EpicFightDamageSource epicFightSource) {
            float value = epicFightSource.calculateImpact();
            return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
        }

        return isFallbackEligible(source.getMsgId()) ? fallbackImpact(damageAmount) : 0.0F;
    }

    static float fallbackImpact(float damageAmount) {
        return Math.max(FALLBACK_MIN_IMPACT,
                Math.min(FALLBACK_MAX_IMPACT, damageAmount / FALLBACK_DIVISOR));
    }

    static boolean isFallbackEligible(String id) {
        if (id == null || id.isBlank()) return true;
        String normalized = id.toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(".", "");
        int colon = normalized.lastIndexOf(':');
        String path = colon >= 0 ? normalized.substring(colon + 1) : normalized;
        return !EXCLUDED_DAMAGE_IDS.contains(path);
    }

    private NeoForgeCompatibilityDamage() {}
}
