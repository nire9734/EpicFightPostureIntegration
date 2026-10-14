package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoArmorBodySkinningContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * Replaces only a safe body-only GeoArmorRenderer subtree when it crosses the
 * humanoid Root/Chest split. Other armor parts retain GeckoLib's normal path.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib.renderer.GeoArmorRenderer", remap = false)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class GeckoArmorRendererBodySkinningMixin {
    @Inject(
            method = "renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/Item;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIIFFFF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void efpi$skinBodyArmorAcrossWaist(
            PoseStack poseStack,
            Item animatable,
            GeoBone bone,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha,
            CallbackInfo ci) {
        GeoArmorRenderer renderer = (GeoArmorRenderer)(Object)this;
        GeoModel geoModel = renderer.getGeoModel();
        BakedGeoModel bakedModel = geoModel.getBakedModel(
                geoModel.getModelResource((GeoAnimatable)renderer.getAnimatable())
        );

        if (GeckoArmorBodySkinningContext.renderBodySubtreeIfActive(
                renderer,
                bakedModel,
                bone,
                poseStack,
                buffer,
                isReRender,
                packedLight,
                packedOverlay,
                red,
                green,
                blue,
                alpha)) {
            ci.cancel();
        }
    }
}
