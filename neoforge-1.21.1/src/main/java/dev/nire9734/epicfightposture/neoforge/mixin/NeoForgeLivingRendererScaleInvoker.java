package dev.nire9734.epicfightposture.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoke the original renderer's model scaling, including GiantMobRenderer's
 * scale override, after Epic Fight has applied its armature transform.
 */
@Mixin(LivingEntityRenderer.class)
public interface NeoForgeLivingRendererScaleInvoker {
    @Invoker("scale")
    void efpi$invokeScale(LivingEntity entity, PoseStack poseStack, float partialTicks);
}
