package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

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
    // Keep the exact factories EFPI installed. A third-party replacement must
    // never be mistaken for our own patch after a world or renderer reload.
    private static final Map<EntityType<?>, Set<Object>> ownedOrdinary = new IdentityHashMap<>();
    private static final Map<EntityType<?>, Set<Object>> ownedCustom = new IdentityHashMap<>();

    enum State {
        MISSING, OWNED, FOREIGN, UNKNOWN
    }

    static synchronized State state(EntityType<?> entityType) {
        if (entityType == null || !resolveFields()) {
            return State.UNKNOWN;
        }
        try {
            Map<?, ?> ordinary = readMap(capabilitiesField);
            Map<?, ?> custom = readMap(customCapabilitiesField);
            if (ordinary == null || custom == null) {
                return State.UNKNOWN;
            }
            boolean found = false;
            if (ordinary.containsKey(entityType)) {
                found = true;
                if (!matches(ownedOrdinary, entityType, ordinary.get(entityType))) {
                    return State.FOREIGN;
                }
            }
            if (custom.containsKey(entityType)) {
                found = true;
                if (!matches(ownedCustom, entityType, custom.get(entityType))) {
                    return State.FOREIGN;
                }
            }
            return found ? State.OWNED : State.MISSING;
        } catch (Throwable throwable) {
            lookupFailed = true;
            warnFailure("could not inspect Epic Fight provider ownership", throwable);
            return State.UNKNOWN;
        }
    }

    static synchronized void recordEventRegistration(EntityType<?> entityType, Object factory) {
        if (entityType != null && factory != null) {
            remember(ownedOrdinary, entityType, factory);
        }
    }

    static synchronized boolean isOwnedEventRegistration(EntityType<?> entityType, Object factory) {
        return matches(ownedOrdinary, entityType, factory);
    }

    /** Record the actual factory stored by Epic Fight, not the input adapter. */
    static synchronized boolean recordRuntimeRegistration(EntityType<?> entityType) {
        if (entityType == null || !resolveFields()) {
            return false;
        }
        try {
            Map<?, ?> map = readMap(customCapabilitiesField);
            if (map == null || !map.containsKey(entityType) || map.get(entityType) == null) {
                return false;
            }
            remember(ownedCustom, entityType, map.get(entityType));
            return true;
        } catch (Throwable throwable) {
            lookupFailed = true;
            warnFailure("could not track EFPI's runtime provider", throwable);
            return false;
        }
    }

    private static void remember(Map<EntityType<?>, Set<Object>> known,
                                 EntityType<?> type, Object factory) {
        known.computeIfAbsent(type,
                unused -> Collections.newSetFromMap(new IdentityHashMap<>())).add(factory);
    }

    private static boolean matches(Map<EntityType<?>, Set<Object>> known,
                                   EntityType<?> type, Object factory) {
        return factory != null && known.containsKey(type) && known.get(type).contains(factory);
    }

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
        Map<?, ?> map = readMap(field);
        if (map == null) {
            lookupFailed = true;
            warnFailure(fieldName + " is not a Map", null);
            return true;
        }
        return map.containsKey(entityType);
    }

    private static Map<?, ?> readMap(Field field) throws IllegalAccessException {
        Object value = field.get(null);
        return value instanceof Map<?, ?> map ? map : null;
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
