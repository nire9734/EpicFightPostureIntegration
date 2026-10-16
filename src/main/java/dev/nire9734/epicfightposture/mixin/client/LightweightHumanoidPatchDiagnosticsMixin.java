package dev.nire9734.epicfightposture.mixin.client;

import dev.nire9734.epicfightposture.client.FrameSpikeDiagnostics;
import dev.nire9734.epicfightposture.compat.humanoid.LightweightHumanoidPatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts normal-vs-special renderer override decisions inside a rendered frame. */
@Mixin(value = LightweightHumanoidPatch.class, remap = false)
public abstract class LightweightHumanoidPatchDiagnosticsMixin {
    @Inject(method = "overrideRender", at = @At("RETURN"), remap = false)
    private void efpi$recordOverrideDecision(CallbackInfoReturnable<Boolean> cir) {
        FrameSpikeDiagnostics.recordOverrideCheck(Boolean.TRUE.equals(cir.getReturnValue()));
    }
}
