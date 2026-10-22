package dev.nire9734.epicfightposture.neoforge.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.neoforge.compat.models.LionfishLayerRenderTargetCapture;
import dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeSpecialRenderHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mirrors Forge's layer target capture for glowing/outline render passes. */
@Mixin(OutlineBufferSource.class)
public abstract class NeoForgeOutlineBufferSourceMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void efpi$captureLayerRenderType(
            RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        if (NeoForgeSpecialRenderHooks.isLayerReplayActive()) {
            LionfishLayerRenderTargetCapture.record(
                    (MultiBufferSource) (Object) this, renderType, cir.getReturnValue());
        }
    }
}
