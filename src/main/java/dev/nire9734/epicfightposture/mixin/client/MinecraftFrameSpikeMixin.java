package dev.nire9734.epicfightposture.mixin.client;

import dev.nire9734.epicfightposture.client.FrameSpikeDiagnostics;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measures complete client frames and emits diagnostics only for >40 ms spikes. */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameSpikeMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void efpi$beginFrame(boolean renderLevel, CallbackInfo ci) {
        FrameSpikeDiagnostics.beginFrame();
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void efpi$endFrame(boolean renderLevel, CallbackInfo ci) {
        FrameSpikeDiagnostics.endFrame();
    }
}
