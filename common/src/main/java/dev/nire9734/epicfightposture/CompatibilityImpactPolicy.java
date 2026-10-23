package dev.nire9734.epicfightposture;

import java.util.Locale;
import java.util.Set;

/** Shared fallback and impact sanitation for Forge and NeoForge damage sources. */
public final class CompatibilityImpactPolicy {
    private static final Set<String> EXCLUDED_DAMAGE_IDS = Set.of(
            "infire", "onfire", "lava", "hotfloor", "inwall", "cramming", "drown", "starve",
            "cactus", "fall", "flyintowall", "outofworld", "outsideborder", "freeze", "stalagmite",
            "sweetberrybush", "dryout", "generickill", "wither", "poison", "bleed");

    public static float epicFightImpact(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
    }

    public static float fallbackImpact(float damageAmount) {
        return Math.max(0.5F, Math.min(5.0F, damageAmount / 4.0F));
    }

    public static boolean isFallbackEligible(String id) {
        if (id == null || id.isBlank()) return true;
        String normalized = id.toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(".", "");
        int colon = normalized.lastIndexOf(':');
        String path = colon >= 0 ? normalized.substring(colon + 1) : normalized;
        return !EXCLUDED_DAMAGE_IDS.contains(path);
    }

    private CompatibilityImpactPolicy() { }
}
