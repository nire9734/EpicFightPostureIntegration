package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishLayerRenderTargetCapture;
import dev.nire9734.epicfightposture.compat.humanoid.client.SpecialRenderHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures the actual consumer returned by Minecraft's outline buffer wrapper. */
@Mixin(OutlineBufferSource.class)
public abstract class OutlineBufferSourceMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void efpi$captureLionfishLayerTarget(
            RenderType renderType,
            CallbackInfoReturnable<VertexConsumer> cir) {
        if (!SpecialRenderHooks.isLayerReplayActive()) {
            return;
        }
        LionfishLayerRenderTargetCapture.record(
                (MultiBufferSource) (Object) this,
                renderType,
                cir.getReturnValue()
        );
    }
}
