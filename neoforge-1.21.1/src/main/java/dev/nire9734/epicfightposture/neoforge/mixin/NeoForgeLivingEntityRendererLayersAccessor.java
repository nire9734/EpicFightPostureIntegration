package dev.nire9734.epicfightposture.neoforge.mixin;

import java.util.List;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to renderer-owned extra passes during EFPI special states. */
@Mixin(LivingEntityRenderer.class)
public interface NeoForgeLivingEntityRendererLayersAccessor {
    @Accessor("layers")
    List<RenderLayer<?, ?>> efpi$getLayers();
}
