package dev.nire9734.epicfightposture.compat.spider;

import dev.nire9734.epicfightposture.animation.PostureAnimations;
import net.shelmarow.combat_evolution.execution.ExecutionTypeManager;
import yesman.epicfight.model.armature.SpiderArmature;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Shared target-side execution logic for standard Epic Fight spider armatures. */
public final class SpiderExecutionSupport {
    public static boolean isCompatible(LivingEntityPatch<?> patch) {
        if (patch == null || PostureAnimations.SPIDER_EXECUTED == null) {
            return false;
        }
        try {
            return patch.getOriginal() != null && patch.getArmature() instanceof SpiderArmature;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static ExecutionTypeManager.Type replaceVictimAnimation(
            LivingEntityPatch<?> patch,
            ExecutionTypeManager.Type originalType) {
        if (originalType == null || !isCompatible(patch)) {
            return originalType;
        }

        return new ExecutionTypeManager.Type(
                originalType.executionAnimation(),
                PostureAnimations.SPIDER_EXECUTED,
                originalType.offset(),
                originalType.rotationOffset(),
                originalType.totalTick()
        );
    }

    private SpiderExecutionSupport() {
    }
}
