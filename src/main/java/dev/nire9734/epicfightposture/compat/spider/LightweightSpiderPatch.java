package dev.nire9734.epicfightposture.compat.spider;

import dev.nire9734.epicfightposture.compat.humanoid.HumanoidRenderState;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import net.minecraft.world.entity.LivingEntity;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.execution.ExecutionTypeManager;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

/** Minimal Epic Fight bridge for automatically detected Minecraft SpiderModel mobs. */
public final class LightweightSpiderPatch extends LivingEntityPatch<LivingEntity>
        implements CustomExecuteEntity {

    @Override
    protected void initAnimator(Animator animator) {
        super.initAnimator(animator);
        animator.addLivingAnimation(LivingMotions.IDLE, Animations.SPIDER_IDLE);
        animator.addLivingAnimation(LivingMotions.DEATH, Animations.SPIDER_DEATH);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        this.currentLivingMotion = this.original.getHealth() <= 0.0F
                ? LivingMotions.DEATH
                : LivingMotions.IDLE;
        this.currentCompositeMotion = this.currentLivingMotion;
    }

    @Override
    public boolean applyStun(StunType stunType, float stunTime) {
        return compatibilityEnabled()
                && stunType == StunType.NEUTRALIZE
                && super.applyStun(stunType, stunTime);
    }

    @Override
    public AssetAccessor<? extends StaticAnimation> getHitAnimation(StunType stunType) {
        return compatibilityEnabled() && stunType == StunType.NEUTRALIZE
                ? Animations.SPIDER_NEUTRALIZED
                : null;
    }

    @Override
    public boolean overrideRender() {
        return compatibilityEnabled() && HumanoidRenderState.shouldOverride(this.original);
    }

    @Override
    public Faction getFaction() {
        return Factions.NEUTRAL;
    }

    @Override
    public boolean canBeExecuted(LivingEntityPatch<?> executorPatch) {
        return compatibilityEnabled() && SpiderExecutionSupport.isCompatible(this);
    }

    @Override
    public boolean canUseCustomType(LivingEntityPatch<?> executorPatch, ExecutionTypeManager.Type originalType) {
        return compatibilityEnabled() && originalType != null && SpiderExecutionSupport.isCompatible(this);
    }

    @Override
    public ExecutionTypeManager.Type getExecutionType(
            LivingEntityPatch<?> executorPatch,
            ExecutionTypeManager.Type originalType) {
        return compatibilityEnabled()
                ? SpiderExecutionSupport.replaceVictimAnimation(this, originalType)
                : originalType;
    }

    @Override
    public boolean canBeAssassinate(LivingEntityPatch<?> executorPatch, LivingEntityPatch<?> targetPatch) {
        return false;
    }

    private boolean compatibilityEnabled() {
        return this.original != null && VanillaHumanoidCompat.isCompatibilityEnabled(this.original.getType());
    }
}
