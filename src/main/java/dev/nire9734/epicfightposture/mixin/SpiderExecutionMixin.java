package dev.nire9734.epicfightposture.mixin;

import dev.nire9734.epicfightposture.animation.PostureAnimations;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.execution.ExecutionTypeManager;
import org.spongepowered.asm.mixin.Mixin;
import yesman.epicfight.model.armature.SpiderArmature;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.mob.SpiderPatch;

/**
 * Adds Combat Evolution target-side execution support to Epic Fight SpiderPatch.
 *
 * <p>The compatibility decision is armature-based rather than entity-ID-based.
 * SpiderPatch subclasses therefore inherit the support automatically, including
 * vanilla CaveSpiderPatch and third-party SpiderPatch subclasses, as long as they
 * actually expose Epic Fight's standard SpiderArmature. Subclasses that replace
 * the armature with an incompatible rig fail closed.</p>
 */
@Mixin(value = SpiderPatch.class, remap = false)
public abstract class SpiderExecutionMixin implements CustomExecuteEntity {
    @Override
    public boolean canBeExecuted(LivingEntityPatch<?> executorPatch) {
        return efpi$usesCompatibleSpiderArmature();
    }

    @Override
    public boolean canUseCustomType(
            LivingEntityPatch<?> executorPatch,
            ExecutionTypeManager.Type originalType
    ) {
        return originalType != null && efpi$usesCompatibleSpiderArmature();
    }

    @Override
    public ExecutionTypeManager.Type getExecutionType(
            LivingEntityPatch<?> executorPatch,
            ExecutionTypeManager.Type originalType
    ) {
        if (originalType == null || !efpi$usesCompatibleSpiderArmature()) {
            return originalType;
        }

        // Preserve Combat Evolution's executor animation, positioning, rotation,
        // and duration. Only replace the victim animation with EFPI's Spider clip.
        return new ExecutionTypeManager.Type(
                originalType.executionAnimation(),
                PostureAnimations.SPIDER_EXECUTED,
                originalType.offset(),
                originalType.rotationOffset(),
                originalType.totalTick()
        );
    }

    @Override
    public boolean canBeAssassinate(
            LivingEntityPatch<?> executorPatch,
            LivingEntityPatch<?> targetPatch
    ) {
        return false;
    }

    private boolean efpi$usesCompatibleSpiderArmature() {
        if (PostureAnimations.SPIDER_EXECUTED == null) {
            return false;
        }

        LivingEntityPatch<?> patch = (LivingEntityPatch<?>) (Object) this;
        try {
            return patch.getOriginal() != null && patch.getArmature() instanceof SpiderArmature;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
