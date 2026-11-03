package dev.nire9734.epicfightposture;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.lang.reflect.Method;

/**
 * Entity-instance safety gate shared by Forge and NeoForge automatic patches.
 *
 * <p>Forge multipart children (PartEntity) reuse their parent's EntityType.
 * Looking up an Epic Fight patch by EntityType alone can therefore select a
 * LivingEntityPatch for a non-living child and throw during construction.
 * The multipart parent is also excluded conservatively: EFPI has not verified
 * that its animation and execution retargeting can preserve independent parts.
 */
public final class AutomaticPatchSafety {
    /**
     * Detects multipart contracts structurally, without invoking virtual
     * methods while a modded subclass constructor is still initializing.
     * A subclass may report false from isMultipartEntity() before its fields
     * are assigned; overriding the contract is therefore a conservative veto.
     */
    public static boolean hasCustomMultipartContract(Class<?> entityClass, Class<?> baseEntityClass) {
        if (entityClass == null || baseEntityClass == null
                || !baseEntityClass.isAssignableFrom(entityClass)) return true;
        try {
            return overridesMethod(entityClass, baseEntityClass, "isMultipartEntity")
                    || overridesMethod(entityClass, baseEntityClass, "getParts");
        } catch (RuntimeException | LinkageError failure) {
            return true;
        }
    }

    private static boolean overridesMethod(Class<?> entityClass, Class<?> baseClass, String name) {
        Class<?> baseOwner = ownerOfPublicMethod(baseClass, name);
        Class<?> entityOwner = ownerOfPublicMethod(entityClass, name);
        return entityOwner != baseOwner;
    }

    private static Class<?> ownerOfPublicMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            return method.getDeclaringClass();
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    public static boolean canAttach(boolean isLivingEntity, boolean hasMultipartContract,
                                    BooleanSupplier multipartCheck) {
        if (!isLivingEntity || hasMultipartContract || multipartCheck == null) return false;
        // Factories run during Entity's superclass constructor. A mod's
        // isMultipartEntity override may touch fields not initialized yet.
        // An unsafe/failed inspection must never abort entity construction.
        try {
            return !multipartCheck.getAsBoolean();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Forge's Epic Fight provider factory expects a non-null Supplier. */
    public static <T> Supplier<T> guardedSupplier(
            boolean isLivingEntity, boolean hasMultipartContract,
            BooleanSupplier multipartCheck, Supplier<T> patchFactory) {
        return canAttach(isLivingEntity, hasMultipartContract, multipartCheck) && patchFactory != null
                ? patchFactory : () -> null;
    }

    private AutomaticPatchSafety() {
    }
}
