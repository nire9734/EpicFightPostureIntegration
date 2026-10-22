package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the source renderer's model-scale hook so EFPI special-state renderers
 * can preserve mod-specific entity sizing.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererScaleInvoker {
    @Invoker("scale")
    void efpi$invokeScale(LivingEntity entity, PoseStack poseStack, float partialTick);
}
