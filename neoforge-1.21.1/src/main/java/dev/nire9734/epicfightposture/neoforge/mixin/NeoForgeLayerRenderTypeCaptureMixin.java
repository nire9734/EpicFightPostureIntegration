package dev.nire9734.epicfightposture.neoforge.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishLayerRenderTargetCapture;
import dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeSpecialRenderHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Capture a layer's original RenderType before triangle mesh substitution. */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class NeoForgeLayerRenderTypeCaptureMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void efpi$captureLayerRenderType(
            RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        if (NeoForgeSpecialRenderHooks.isLayerReplayActive()) {
            LionfishLayerRenderTargetCapture.record(
                    (MultiBufferSource) (Object) this, renderType, cir.getReturnValue());
        }
    }
}
