package dev.nire9734.epicfightposture.mixin;

import dev.nire9734.epicfightposture.ExecutionRange;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Server side: weapon-scaled ranges for Combat Evolution executions and assassinations.
 * Combat Evolution passes fixed ranges (4.0 execution, 2.0 assassination);
 * {@link ExecutionRange} scales them by the held weapon and the server's
 * execution_range_multiplier. Injections are optional: on a Combat Evolution
 * build without these calls, its own ranges stay in effect.
 */
@Mixin(value = net.shelmarow.combat_evolution.execution.ExecutionHandler.class, remap = false)
public abstract class ExecutionRangeMixin {
    private static final String LOOKED_AT =
            "Lnet/shelmarow/combat_evolution/execution/ExecutionHandler;getEntityLookedAt(Lnet/minecraft/world/entity/player/Player;D)Lnet/minecraft/world/entity/LivingEntity;";
    private static final String IN_RANGE =
            "Lnet/shelmarow/combat_evolution/execution/ExecutionHandler;targetIsInRange(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;DDD)Z";
    private static final String BEHIND =
            "Lnet/shelmarow/combat_evolution/execution/ExecutionHandler;isBehindTarget(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;D)Z";

    @ModifyArg(method = {"tryExecute", "tryAssassinate"},
            at = @At(value = "INVOKE", target = LOOKED_AT), index = 1, require = 0)
    private static double efpi$lookRange(Player player, double range) {
        return ExecutionRange.scaled(player, range);
    }

    @ModifyArg(method = "tryExecute", at = @At(value = "INVOKE", target = IN_RANGE), index = 3, require = 0)
    private static double efpi$executionRange(LivingEntity player, LivingEntity target,
                                              double minRange, double maxRange, double angle) {
        return ExecutionRange.scaledToTarget(player, target, maxRange);
    }

    @ModifyArg(method = {"tryAssassinate", "canPlayerAssassinate"},
            at = @At(value = "INVOKE", target = BEHIND), index = 2, require = 0)
    private static double efpi$assassinationRange(LivingEntity player, LivingEntity target, double range) {
        return ExecutionRange.scaledToTarget(player, target, range);
    }
}
