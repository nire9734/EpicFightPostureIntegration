package dev.nire9734.epicfightposture.neoforge.compat;

import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.Faction;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

/**
 * Non-invasive patch: preserve the target mob's original AI and renderer.
 * Only guard-break stun is enabled; other Epic Fight hit reactions are denied.
 * Special animated rendering is deliberately deferred until the renderer
 * has passed an explicit geometry/armature compatibility test.
 */
public final class NeoForgeLightweightHumanoidPatch<T extends LivingEntity> extends LivingEntityPatch<T> {
    public NeoForgeLightweightHumanoidPatch(T entity) {
        super(entity);
    }

    @Override
    protected void initAnimator(Animator animator) {
        super.initAnimator(animator);
        animator.addLivingAnimation(LivingMotions.IDLE, Animations.BIPED_IDLE);
        animator.addLivingAnimation(LivingMotions.DEATH, Animations.BIPED_DEATH);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        currentLivingMotion = original.getHealth() <= 0.0F ? LivingMotions.DEATH : LivingMotions.IDLE;
        currentCompositeMotion = currentLivingMotion;
    }

    @Override
    public boolean applyStun(StunType stunType, float time) {
        return stunType == StunType.NEUTRALIZE
                && NeoForgeAutoCompatibility.isEnabled(original.getType())
                && super.applyStun(stunType, time);
    }

    @Override
    public AssetAccessor<? extends StaticAnimation> getHitAnimation(StunType stunType) {
        return stunType == StunType.NEUTRALIZE && NeoForgeAutoCompatibility.isEnabled(original.getType())
                ? Animations.BIPED_COMMON_NEUTRALIZED
                : null;
    }

    @Override
    public boolean overrideRender() {
        // No override without a verified client-side runtime mesh and a
        // server-synchronized guard-break/execution state.
        return NeoForgeHumanoidRenderGate.shouldOverride(original);
    }

    @Override
    public Faction getFaction() {
        return Factions.NEUTRAL;
    }
}
