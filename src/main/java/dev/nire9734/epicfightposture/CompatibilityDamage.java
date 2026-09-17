package dev.nire9734.epicfightposture;

import net.minecraft.world.damagesource.DamageSource;

import java.util.Locale;
import java.util.Set;

public final class CompatibilityDamage {
    private static final float FALLBACK_DIVISOR = 4.0F;
    private static final float FALLBACK_MIN_IMPACT = 0.5F;
    private static final float FALLBACK_MAX_IMPACT = 3.0F;

    private static final Set<String> EXCLUDED_DAMAGE_IDS = Set.of(
            "infire", "onfire", "lava", "hotfloor", "inwall", "cramming", "drown", "starve",
            "cactus", "fall", "flyintowall", "outofworld", "outsideborder", "freeze", "stalagmite",
            "sweetberrybush", "dryout", "generickill", "wither", "poison", "bleed"
    );

    public static float calculateImpact(Object source, float damageAmount) {
        if (source == null) return 0.0F;
        if (isEpicFightDamageSource(source)) return epicFightImpact(source);
        if (!Float.isFinite(damageAmount) || damageAmount <= 0.0F) return 0.0F;
        if (!isFallbackEligible(source)) return 0.0F;
        return Math.max(FALLBACK_MIN_IMPACT, Math.min(FALLBACK_MAX_IMPACT, damageAmount / FALLBACK_DIVISOR));
    }

    private static boolean isEpicFightDamageSource(Object source) {
        for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass()) {
            if ("yesman.epicfight.world.damagesource.EpicFightDamageSource".equals(type.getName())) return true;
        }
        return false;
    }

    private static float epicFightImpact(Object source) {
        try {
            Object result = FastReflection.invokeNoArg(source, new String[]{"calculateImpact"});
            if (result instanceof Number number) {
                float value = number.floatValue();
                return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
            }
        } catch (Throwable ignored) {
        }
        return 0.0F;
    }

    private static boolean isFallbackEligible(Object source) {
        String id = damageId(source);
        if (id == null || id.isBlank()) return true;
        String normalized = id.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(".", "");
        int colon = normalized.lastIndexOf(':');
        String path = colon >= 0 ? normalized.substring(colon + 1) : normalized;
        return !EXCLUDED_DAMAGE_IDS.contains(path);
    }

    private static String damageId(Object source) {
        if (source instanceof DamageSource damageSource) {
            return damageSource.getMsgId();
        }
        Object result = FastReflection.invokeNoArg(source, new String[]{"getMsgId", "m_19385_", "e"});
        return result instanceof String string ? string : null;
    }

    private CompatibilityDamage() {}
}
