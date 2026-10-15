package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Read-only ownership probe for Epic Fight custom entity patches.
 *
 * <p>Epic Fight 1.20.1 keeps addon registrations in the private
 * {@code CUSTOM_CAPABILITIES} map. {@link EntityPatchProvider#getPatchedEntities()}
 * exposes only the ordinary registry, so relying on that method alone can make an
 * addon-owned entity look unpatched and let EFPI overwrite its specialized patch.
 *
 * <p>Automatic compatibility is deliberately fail-closed if the custom registry
 * cannot be inspected. Overwriting an unknown addon patch is less safe than
 * skipping EFPI compatibility for that target.
 */
final class EpicFightPatchOwnership {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Field customCapabilitiesField;
    private static volatile boolean lookupAttempted;
    private static volatile boolean lookupFailed;
    private static volatile boolean failureLogged;

    static boolean hasCustomPatch(EntityType<?> entityType) {
        if (entityType == null) {
            return true;
        }
        Field field = customCapabilitiesField();
        if (field == null) {
            return true;
        }
        try {
            Object value = field.get(null);
            if (!(value instanceof Map<?, ?> map)) {
                lookupFailed = true;
                warnFailure("CUSTOM_CAPABILITIES is not a Map", null);
                return true;
            }
            return map.containsKey(entityType);
        } catch (Throwable throwable) {
            lookupFailed = true;
            warnFailure("could not read CUSTOM_CAPABILITIES", throwable);
            return true;
        }
    }

    private static Field customCapabilitiesField() {
        if (lookupAttempted) {
            return lookupFailed ? null : customCapabilitiesField;
        }
        synchronized (EpicFightPatchOwnership.class) {
            if (lookupAttempted) {
                return lookupFailed ? null : customCapabilitiesField;
            }
            try {
                Field field = EntityPatchProvider.class.getDeclaredField("CUSTOM_CAPABILITIES");
                field.setAccessible(true);
                customCapabilitiesField = field;
            } catch (Throwable throwable) {
                lookupFailed = true;
                warnFailure("could not resolve CUSTOM_CAPABILITIES", throwable);
            } finally {
                lookupAttempted = true;
            }
            return lookupFailed ? null : customCapabilitiesField;
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
            LOGGER.warn("EFPI cannot safely inspect Epic Fight custom patch ownership ({}); automatic compatibility will fail closed", detail);
        } else {
            LOGGER.warn("EFPI cannot safely inspect Epic Fight custom patch ownership ({}); automatic compatibility will fail closed", detail, throwable);
        }
    }

    private EpicFightPatchOwnership() {
    }
}
