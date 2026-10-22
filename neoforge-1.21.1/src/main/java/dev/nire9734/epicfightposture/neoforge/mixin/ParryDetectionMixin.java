package dev.nire9734.epicfightposture.neoforge.mixin;

import dev.nire9734.epicfightposture.NeoForgePostureServerEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.event.types.entity.TakeDamageEvent;
import yesman.epicfight.skill.guard.GuardSkill;

/**
 * Epic Fight 21.17: successful parries set the flag on Income, not Attack.
 * Capture the transition once per event to avoid duplicate posture damage.
 */
@Mixin(value = TakeDamageEvent.Income.class, remap = false)
public abstract class ParryDetectionMixin {
    @Unique private boolean efpi$parryHandled;

    @Inject(method = "setParried", at = @At("TAIL"))
    private void efpi$handleParry(boolean parried, CallbackInfo callback) {
        if (!parried || this.efpi$parryHandled) return;
        TakeDamageEvent.Income event = (TakeDamageEvent.Income) (Object) this;
        Entity attacker = GuardSkill.getOffender(event.getDamageSource());
        if (!(attacker instanceof LivingEntity living)) return;
        this.efpi$parryHandled = true;
        NeoForgePostureServerEvents.onParried(living);
    }
}
