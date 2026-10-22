package dev.nire9734.epicfightposture.mixin;

import dev.nire9734.epicfightposture.PostureManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.world.entity.eventlistener.TakeDamageEvent;

/**
 * Detects successful Epic Fight-style parries at the point where the event flag is set.
 * This avoids listener-order races between Epic Fight, Combat Evolution, and other addons
 * that may mark the same TAKE_DAMAGE_EVENT_ATTACK event as parried at priority -1.
 */
@Mixin(value = TakeDamageEvent.Attack.class, remap = false)
public abstract class ParryDetectionMixin {
    @Unique
    private boolean efpi$parryHandled;

    @Inject(method = "setParried", at = @At("TAIL"))
    private void efpi$onParried(boolean parried, CallbackInfo ci) {
        if (!parried || this.efpi$parryHandled) {
            return;
        }

        TakeDamageEvent.Attack event = (TakeDamageEvent.Attack) (Object) this;
        Entity offender = GuardSkill.getOffender(event.getDamageSource());
        if (!(offender instanceof LivingEntity livingEntity)) {
            return;
        }

        this.efpi$parryHandled = true;
        PostureManager.onParried(livingEntity);
    }
}
