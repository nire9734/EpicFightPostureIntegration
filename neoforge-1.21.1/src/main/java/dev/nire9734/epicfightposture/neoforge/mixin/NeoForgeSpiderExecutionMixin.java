package dev.nire9734.epicfightposture.neoforge.mixin;

import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeSpiderExecutionSupport;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.execution.ExecutionTypeManager;
import org.spongepowered.asm.mixin.Mixin;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.mob.SpiderPatch;

/** CE target-side execution support for already Epic Fight-owned spiders. */
@Mixin(value = SpiderPatch.class, remap = false)
public abstract class NeoForgeSpiderExecutionMixin implements CustomExecuteEntity {
    @Override
    public boolean canBeExecuted(LivingEntityPatch<?> executorPatch) {
        return NeoForgeSpiderExecutionSupport.isCompatible((LivingEntityPatch<?>) (Object) this);
    }

    @Override
    public boolean canUseCustomType(LivingEntityPatch<?> executorPatch,
                                    ExecutionTypeManager.Type originalType) {
        return originalType != null
                && NeoForgeSpiderExecutionSupport.isCompatible((LivingEntityPatch<?>) (Object) this);
    }

    @Override
    public ExecutionTypeManager.Type getExecutionType(LivingEntityPatch<?> executorPatch,
                                                       ExecutionTypeManager.Type originalType) {
        return NeoForgeSpiderExecutionSupport.replaceVictimAnimation(
                (LivingEntityPatch<?>) (Object) this, originalType);
    }

    @Override
    public boolean canBeAssassinate(LivingEntityPatch<?> executorPatch,
                                    LivingEntityPatch<?> targetPatch) {
        return false;
    }
}
