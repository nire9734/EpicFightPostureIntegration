package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishLayerRenderTargetCapture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records the source RenderType selected during EFPI RenderLayer replay. */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class MultiBufferSourceBufferSourceMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void efpi$captureLionfishLayerRenderType(
            RenderType renderType,
            CallbackInfoReturnable<VertexConsumer> cir) {
        LionfishLayerRenderTargetCapture.record(
                (MultiBufferSource) (Object) this,
                renderType,
                cir.getReturnValue()
        );
    }
}
