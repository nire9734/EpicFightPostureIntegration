package dev.nire9734.epicfightposture.neoforge.compat;

import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.execution.ExecutionTypeManager;
import net.minecraft.world.entity.Mob;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;
/** Minimal spider bridge: leave ordinary Mob AI/renderer unmodified. */
public final class NeoForgeLightweightSpiderPatch<T extends Mob> extends LivingEntityPatch<T>
        implements CustomExecuteEntity {
    public NeoForgeLightweightSpiderPatch(T mob) { super(mob); }
    @Override protected void initAnimator(Animator animator) {
        super.initAnimator(animator);
        animator.addLivingAnimation(LivingMotions.IDLE, Animations.SPIDER_IDLE);
        animator.addLivingAnimation(LivingMotions.DEATH, Animations.SPIDER_DEATH);
    }
    @Override public void updateMotion(boolean considerInaction) {
        currentLivingMotion = original.getHealth() <= 0 ? LivingMotions.DEATH : LivingMotions.IDLE;
        currentCompositeMotion = currentLivingMotion;
    }
    @Override public boolean applyStun(StunType type, float time) {
        return type == StunType.NEUTRALIZE && NeoForgeAutoCompatibility.isEnabled(original.getType())
                && super.applyStun(type, time);
    }
    @Override public AssetAccessor<? extends StaticAnimation> getHitAnimation(StunType type) {
        return type == StunType.NEUTRALIZE && NeoForgeAutoCompatibility.isEnabled(original.getType())
                ? Animations.SPIDER_NEUTRALIZED : null;
    }
    @Override public boolean overrideRender() {
        return NeoForgeHumanoidRenderGate.shouldOverride(original);
    }
    @Override public Faction getFaction() { return Factions.NEUTRAL; }

    @Override public boolean canBeExecuted(LivingEntityPatch<?> executorPatch) {
        return executionCompatible();
    }
    @Override public boolean canUseCustomType(LivingEntityPatch<?> executorPatch,
                                               ExecutionTypeManager.Type originalType) {
        return originalType != null && executionCompatible();
    }
    @Override public ExecutionTypeManager.Type getExecutionType(LivingEntityPatch<?> executorPatch,
                                                                  ExecutionTypeManager.Type originalType) {
        return executionCompatible()
                ? NeoForgeSpiderExecutionSupport.replaceVictimAnimation(this, originalType)
                : originalType;
    }
    @Override public boolean canBeAssassinate(LivingEntityPatch<?> executorPatch,
                                               LivingEntityPatch<?> targetPatch) {
        return false;
    }

    private boolean executionCompatible() {
        return original != null && NeoForgeAutoCompatibility.isEnabled(original.getType())
                && NeoForgeSpiderExecutionSupport.isCompatible(this);
    }
}
