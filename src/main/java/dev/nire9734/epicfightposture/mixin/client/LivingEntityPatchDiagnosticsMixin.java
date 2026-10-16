package dev.nire9734.epicfightposture.mixin.client;

import dev.nire9734.epicfightposture.client.FrameSpikeDiagnostics;
import dev.nire9734.epicfightposture.compat.humanoid.LightweightHumanoidPatch;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Times only EFPI-owned lightweight patch ticks inside a rendered frame. */
@Mixin(value = LivingEntityPatch.class, remap = false)
public abstract class LivingEntityPatchDiagnosticsMixin {
    @Unique
    private long efpi$lightweightTickStartedNs;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void efpi$beginLightweightTick(LivingEvent.LivingTickEvent event, CallbackInfo ci) {
        if ((Object) this instanceof LightweightHumanoidPatch) {
            this.efpi$lightweightTickStartedNs = FrameSpikeDiagnostics.startTimedSection();
        } else {
            this.efpi$lightweightTickStartedNs = 0L;
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void efpi$endLightweightTick(LivingEvent.LivingTickEvent event, CallbackInfo ci) {
        long startedNs = this.efpi$lightweightTickStartedNs;
        this.efpi$lightweightTickStartedNs = 0L;
        FrameSpikeDiagnostics.finishLightweightPatchTick(startedNs);
    }
}
