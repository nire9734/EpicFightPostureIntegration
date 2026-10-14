package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoArmorBodySkinningContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;

/** Records which source Gecko bone initiated the current armor callback. */
@Pseudo
@Mixin(targets = "software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer", remap = false)
public abstract class GeckoItemArmorLayerContextMixin {
    @Inject(
            method = "renderForBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/LivingEntity;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void efpi$beginArmorBone(
            PoseStack poseStack,
            LivingEntity animatable,
            GeoBone bone,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            float partialTick,
            int packedLight,
            int packedOverlay,
            CallbackInfo ci) {
        GeckoArmorBodySkinningContext.beginArmorBone(bone);
    }

    @Inject(
            method = "renderForBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/LivingEntity;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void efpi$endArmorBone(
            PoseStack poseStack,
            LivingEntity animatable,
            GeoBone bone,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            float partialTick,
            int packedLight,
            int packedOverlay,
            CallbackInfo ci) {
        GeckoArmorBodySkinningContext.endArmorBone();
    }
}
