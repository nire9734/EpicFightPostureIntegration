package dev.nire9734.epicfightposture;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

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
    public static boolean canAttach(boolean isLivingEntity, BooleanSupplier multipartCheck) {
        if (!isLivingEntity || multipartCheck == null) return false;
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
            boolean isLivingEntity, BooleanSupplier multipartCheck, Supplier<T> patchFactory) {
        return canAttach(isLivingEntity, multipartCheck) && patchFactory != null
                ? patchFactory : () -> null;
    }

    private AutomaticPatchSafety() {
    }
}
