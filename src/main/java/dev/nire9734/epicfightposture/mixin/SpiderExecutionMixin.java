package dev.nire9734.epicfightposture.mixin;

import dev.nire9734.epicfightposture.compat.spider.SpiderExecutionSupport;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.execution.ExecutionTypeManager;
import org.spongepowered.asm.mixin.Mixin;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.mob.SpiderPatch;

/** Adds Combat Evolution target-side execution support to compatible SpiderPatch subclasses. */
@Mixin(value = SpiderPatch.class, remap = false)
public abstract class SpiderExecutionMixin implements CustomExecuteEntity {
    @Override
    public boolean canBeExecuted(LivingEntityPatch<?> executorPatch) {
        return SpiderExecutionSupport.isCompatible((LivingEntityPatch<?>) (Object) this);
    }

    @Override
    public boolean canUseCustomType(
            LivingEntityPatch<?> executorPatch,
            ExecutionTypeManager.Type originalType) {
        return originalType != null
                && SpiderExecutionSupport.isCompatible((LivingEntityPatch<?>) (Object) this);
    }

    @Override
    public ExecutionTypeManager.Type getExecutionType(
            LivingEntityPatch<?> executorPatch,
            ExecutionTypeManager.Type originalType) {
        return SpiderExecutionSupport.replaceVictimAnimation(
                (LivingEntityPatch<?>) (Object) this,
                originalType
        );
    }

    @Override
    public boolean canBeAssassinate(
            LivingEntityPatch<?> executorPatch,
            LivingEntityPatch<?> targetPatch) {
        return false;
    }
}
