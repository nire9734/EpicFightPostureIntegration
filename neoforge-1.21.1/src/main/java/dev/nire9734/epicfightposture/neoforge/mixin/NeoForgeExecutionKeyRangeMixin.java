package dev.nire9734.epicfightposture.neoforge.mixin;

import dev.nire9734.epicfightposture.ExecutionRange;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Client side: the execution key uses the same weapon-scaled range as the server.
 * Combat Evolution passes fixed ranges (4.0 execution, 2.0 assassination);
 * {@link ExecutionRange} scales them by the held weapon and the server's
 * execution_range_multiplier. Injections are optional: on a Combat Evolution
 * build without these calls, its own ranges stay in effect.
 */
@Mixin(value = net.shelmarow.combat_evolution.key.CEKeyHandler.class, remap = false)
public abstract class NeoForgeExecutionKeyRangeMixin {
    private static final String LOOKED_AT =
            "Lnet/shelmarow/combat_evolution/execution/ExecutionHandler;getEntityLookedAt(Lnet/minecraft/world/entity/player/Player;D)Lnet/minecraft/world/entity/LivingEntity;";
    private static final String IN_RANGE =
            "Lnet/shelmarow/combat_evolution/execution/ExecutionHandler;targetIsInRange(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;DDD)Z";
    private static final String BEHIND =
            "Lnet/shelmarow/combat_evolution/execution/ExecutionHandler;isBehindTarget(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;D)Z";

    /** The key handler only sends a request when a target is within this range. */
    @ModifyArg(method = "onKeyInput", at = @At(value = "INVOKE", target = LOOKED_AT), index = 1, require = 0)
    private static double efpi$keyLookRange(Player player, double range) {
        return ExecutionRange.scaled(player, range);
    }
}
