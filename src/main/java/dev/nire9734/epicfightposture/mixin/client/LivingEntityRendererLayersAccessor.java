package dev.nire9734.epicfightposture.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Exposes source RenderLayer instances so EFPI can preserve renderer-owned visual passes. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererLayersAccessor {
    @Accessor("layers")
    List<RenderLayer<?, ?>> efpi$getLayers();
}
