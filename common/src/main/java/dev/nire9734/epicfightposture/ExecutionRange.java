package dev.nire9734.epicfightposture;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

import java.util.function.DoubleSupplier;

/**
 * Runtime side of {@link ExecutionRangePolicy}, called from the Combat Evolution
 * mixins on both loaders. Reads the held weapon's Epic Fight collider through
 * its public serialized form (center and half size), so any weapon with an Epic
 * Fight capability, including addon weapons, is measured the same way.
 *
 * Any failure returns Combat Evolution's own range unchanged.
 */
public final class ExecutionRange {
    private static volatile DoubleSupplier serverMultiplier = () -> 0.0D;
    private static volatile DoubleSupplier clientMultiplier = () -> 0.0D;

    /** Server: the local config. Client: the value synchronized from the server. */
    public static void install(DoubleSupplier server, DoubleSupplier client) {
        serverMultiplier = server == null ? () -> 0.0D : server;
        clientMultiplier = client == null ? () -> 0.0D : client;
    }

    /** One of Combat Evolution's ranges, scaled by the attacker's weapon. */
    public static double scaled(Entity attacker, double combatEvolutionRange) {
        try {
            double multiplier = multiplier(attacker);
            if (!ExecutionRangePolicy.isEnabled(multiplier)) return combatEvolutionRange;
            return ExecutionRangePolicy.range(combatEvolutionRange, multiplier, weaponReach(attacker));
        } catch (RuntimeException | LinkageError failure) {
            return combatEvolutionRange;
        }
    }

    /** As {@link #scaled}, plus an allowance for targets wider than a player. */
    public static double scaledToTarget(Entity attacker, Entity target, double combatEvolutionRange) {
        try {
            double multiplier = multiplier(attacker);
            if (!ExecutionRangePolicy.isEnabled(multiplier)) return combatEvolutionRange;
            double width = target == null ? Double.NaN : target.getBbWidth();
            return ExecutionRangePolicy.range(combatEvolutionRange, multiplier, weaponReach(attacker))
                    + ExecutionRangePolicy.sizeBonus(width, multiplier);
        } catch (RuntimeException | LinkageError failure) {
            return combatEvolutionRange;
        }
    }

    private static double multiplier(Entity attacker) {
        if (attacker == null) return 0.0D;
        return (attacker.level().isClientSide() ? clientMultiplier : serverMultiplier).getAsDouble();
    }

    /** Hand-to-tip reach of the main-hand weapon's collider, or NaN when unknown. */
    static double weaponReach(Entity attacker) {
        if (!(attacker instanceof LivingEntity living)) return Double.NaN;
        CapabilityItem capability = EpicFightCapabilities.getItemStackCapability(living.getMainHandItem());
        if (capability == null) return Double.NaN;
        Collider collider = capability.getWeaponCollider();
        if (collider == null) return Double.NaN;
        CompoundTag tag = collider.serialize(new CompoundTag());
        if (tag == null) return Double.NaN;
        ListTag center = tag.getList("center", Tag.TAG_DOUBLE);
        ListTag size = tag.getList("size", Tag.TAG_DOUBLE);
        if (center.size() < 3 || size.size() < 3) return Double.NaN;
        return ExecutionRangePolicy.colliderReach(
                center.getDouble(0), center.getDouble(1), center.getDouble(2),
                size.getDouble(0), size.getDouble(1), size.getDouble(2));
    }

    private ExecutionRange() {
    }
}
