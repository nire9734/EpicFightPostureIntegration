package dev.nire9734.epicfightposture.neoforge.compat;

import dev.nire9734.epicfightposture.neoforge.animation.NeoForgePostureAnimations;
import net.shelmarow.combat_evolution.execution.ExecutionTypeManager;
import yesman.epicfight.model.armature.SpiderArmature;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Keep CE's attacker animation/offset; replace only verified spider victim poses. */
public final class NeoForgeSpiderExecutionSupport {
    public static boolean isCompatible(LivingEntityPatch<?> patch) {
        if (patch == null || NeoForgePostureAnimations.SPIDER_EXECUTED == null) return false;
        try {
            return patch.getOriginal() != null && patch.getArmature() instanceof SpiderArmature;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    public static ExecutionTypeManager.Type replaceVictimAnimation(
            LivingEntityPatch<?> patch, ExecutionTypeManager.Type originalType) {
        if (originalType == null || !isCompatible(patch)) return originalType;
        return new ExecutionTypeManager.Type(
                originalType.executionAnimation(),
                NeoForgePostureAnimations.SPIDER_EXECUTED,
                originalType.offset(),
                originalType.rotationOffset(),
                originalType.totalTick());
    }

    private NeoForgeSpiderExecutionSupport() {}
}
