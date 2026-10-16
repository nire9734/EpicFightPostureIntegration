package dev.nire9734.epicfightposture;

import net.minecraft.world.damagesource.DamageSource;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;

import java.util.Locale;
import java.util.Set;

public final class CompatibilityDamage {
    private static final float FALLBACK_DIVISOR = 4.0F;
    private static final float FALLBACK_MIN_IMPACT = 0.5F;
    private static final float FALLBACK_MAX_IMPACT = 5.0F;

    private static final Set<String> EXCLUDED_DAMAGE_IDS = Set.of(
            "infire", "onfire", "lava", "hotfloor", "inwall", "cramming", "drown", "starve",
            "cactus", "fall", "flyintowall", "outofworld", "outsideborder", "freeze", "stalagmite",
            "sweetberrybush", "dryout", "generickill", "wither", "poison", "bleed"
    );

    /**
     * Returns posture-weighted impact for a direct player hit.
     * Epic Fight attacks keep their real Impact; non-EF attacks derive an approximate Impact from dealt damage.
     * Both paths receive the same sublinear dealt-damage scaling so player and boss stat scaling stay comparable.
     */
    public static float calculateImpact(DamageSource source, float damageAmount) {
        if (source == null || !Float.isFinite(damageAmount) || damageAmount <= 0.0F) {
            return 0.0F;
        }

        float baseImpact;
        if (source instanceof EpicFightDamageSource epicFightSource) {
            float value = epicFightSource.calculateImpact();
            baseImpact = Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
        } else {
            if (!isFallbackEligible(source)) {
                return 0.0F;
            }
            baseImpact = fallbackImpact(damageAmount);
        }

        if (baseImpact <= 0.0F) {
            return 0.0F;
        }

        float damageScale = PostureBalance.attackDamageScale(
                damageAmount,
                PostureServerConfig.attackDamageReference(),
                PostureServerConfig.attackDamageScalingExponent()
        );
        return baseImpact * damageScale;
    }

    private static float fallbackImpact(float damageAmount) {
        return Math.max(
                FALLBACK_MIN_IMPACT,
                Math.min(FALLBACK_MAX_IMPACT, damageAmount / FALLBACK_DIVISOR)
        );
    }

    private static boolean isFallbackEligible(DamageSource source) {
        String id = source.getMsgId();
        if (id == null || id.isBlank()) return true;
        String normalized = id.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(".", "");
        int colon = normalized.lastIndexOf(':');
        String path = colon >= 0 ? normalized.substring(colon + 1) : normalized;
        return !EXCLUDED_DAMAGE_IDS.contains(path);
    }

    private CompatibilityDamage() {}
}
