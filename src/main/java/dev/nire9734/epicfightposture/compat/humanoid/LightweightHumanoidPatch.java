package dev.nire9734.epicfightposture.compat.humanoid;

import dev.nire9734.epicfightposture.client.ClientPostureStore;
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
 * Minimal Epic Fight bridge for mobs whose original renderer uses HumanoidModel.
 *
 * <p>This intentionally does not extend MobPatch. Epic Fight therefore does not
 * install/replace mob combat goals through this patch. The original mod remains
 * responsible for AI, movement, attacks and normal rendering. The BIPED animator
 * exists only for EFPI posture break and Combat Evolution execution animations.</p>
 */
public final class LightweightHumanoidPatch extends LivingEntityPatch<LivingEntity> {
    public LightweightHumanoidPatch() {
    }

    @Override
    protected void initAnimator(Animator animator) {
        super.initAnimator(animator);
        animator.addLivingAnimation(LivingMotions.IDLE, Animations.BIPED_IDLE);
        animator.addLivingAnimation(LivingMotions.DEATH, Animations.BIPED_DEATH);
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
        // Do not opt compatibility mobs into Epic Fight's ordinary hit reactions.
        // EFPI only needs NEUTRALIZE when posture reaches zero.
        if (stunType != StunType.NEUTRALIZE) {
            return false;
        }
        return super.applyStun(stunType, stunTime);
    }

    @Override
    public AssetAccessor<? extends StaticAnimation> getHitAnimation(StunType stunType) {
        return stunType == StunType.NEUTRALIZE
                ? Animations.BIPED_COMMON_NEUTRALIZED
                : null;
    }

    /**
     * Keep the source mod's renderer for normal gameplay. Epic Fight's patched
     * renderer is allowed to replace it only while EFPI says the target is in a
     * special posture state. ClientPostureStore's broken flag is true for both
     * BROKEN and EXECUTING, so the renderer stays switched for the whole CE
     * execution and returns to the source renderer afterwards.
     */
    @Override
    public boolean overrideRender() {
        if (this.original == null || !this.original.level().isClientSide()) {
            return false;
        }

        ClientPostureStore.Entry state = ClientPostureStore.get(
                this.original.getId(),
                this.original.getUUID()
        );
        return state != null && state.broken;
    }

    @Override
    public Faction getFaction() {
        return Factions.NEUTRAL;
    }
}
