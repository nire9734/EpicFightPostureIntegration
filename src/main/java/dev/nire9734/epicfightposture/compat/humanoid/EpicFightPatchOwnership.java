package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Read-only ownership probe for Epic Fight entity patches.
 *
 * <p>Epic Fight 1.20.1 stores event-registered providers in the private
 * {@code CAPABILITIES} map and runtime overrides in {@code CUSTOM_CAPABILITIES}.
 * Its public {@link EntityPatchProvider#getPatchedEntities()} helper deliberately
 * filters out {@code MobCategory.MISC}, so it is not a complete ownership query.
 * Addons can therefore own a valid ordinary patch that the public list omits.
 *
 * <p>Automatic compatibility is deliberately fail-closed if either provider map
 * cannot be inspected. Overwriting an unknown addon patch is less safe than
 * skipping EFPI compatibility for that target.
 */
final class EpicFightPatchOwnership {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Field capabilitiesField;
    private static volatile Field customCapabilitiesField;
    private static volatile boolean lookupAttempted;
    private static volatile boolean lookupFailed;
    private static volatile boolean failureLogged;

    /**
     * Historical method name retained for the existing call sites. This checks
     * both Epic Fight's ordinary and custom patch registries.
     */
    static boolean hasCustomPatch(EntityType<?> entityType) {
        return hasRegisteredPatch(entityType);
    }

    static boolean hasRegisteredPatch(EntityType<?> entityType) {
        if (entityType == null) {
            return true;
        }
        if (!resolveFields()) {
            return true;
        }
        try {
            return contains(capabilitiesField, "CAPABILITIES", entityType)
                    || contains(customCapabilitiesField, "CUSTOM_CAPABILITIES", entityType);
        } catch (Throwable throwable) {
            lookupFailed = true;
            warnFailure("could not read Epic Fight provider maps", throwable);
            return true;
        }
    }

    private static boolean contains(
            Field field,
            String fieldName,
            EntityType<?> entityType) throws IllegalAccessException {
        Object value = field.get(null);
        if (!(value instanceof Map<?, ?> map)) {
            lookupFailed = true;
            warnFailure(fieldName + " is not a Map", null);
            return true;
        }
        return map.containsKey(entityType);
    }

    private static boolean resolveFields() {
        if (lookupAttempted) {
            return !lookupFailed;
        }
        synchronized (EpicFightPatchOwnership.class) {
            if (lookupAttempted) {
                return !lookupFailed;
            }
            try {
                Field ordinary = EntityPatchProvider.class.getDeclaredField("CAPABILITIES");
                ordinary.setAccessible(true);
                Field custom = EntityPatchProvider.class.getDeclaredField("CUSTOM_CAPABILITIES");
                custom.setAccessible(true);
                capabilitiesField = ordinary;
                customCapabilitiesField = custom;
            } catch (Throwable throwable) {
                lookupFailed = true;
                warnFailure("could not resolve CAPABILITIES/CUSTOM_CAPABILITIES", throwable);
            } finally {
                lookupAttempted = true;
            }
            return !lookupFailed;
        }
    }

    private static void warnFailure(String detail, Throwable throwable) {
        synchronized (EpicFightPatchOwnership.class) {
            if (failureLogged) {
                return;
            }
            failureLogged = true;
        }
        if (throwable == null) {
            LOGGER.warn("EFPI cannot safely inspect Epic Fight patch ownership ({}); automatic compatibility will fail closed", detail);
        } else {
            LOGGER.warn("EFPI cannot safely inspect Epic Fight patch ownership ({}); automatic compatibility will fail closed", detail, throwable);
        }
    }

    private EpicFightPatchOwnership() {
    }
}
