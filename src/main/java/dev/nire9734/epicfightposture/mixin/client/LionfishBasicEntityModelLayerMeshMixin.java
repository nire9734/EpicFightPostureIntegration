package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishPrimaryModelLayerBridge;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishSecondaryModelLayerBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional Lionfish hook for primary and safely deformable secondary layer meshes. */
@Pseudo
@Mixin(targets = "com.github.L_Ender.lionfishapi.client.model.tools.BasicEntityModel", remap = false)
public abstract class LionfishBasicEntityModelLayerMeshMixin {
    @Inject(
            method = "renderToBuffer",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void efpi$reuseSkinnedLayerMesh(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha,
            CallbackInfo ci) {
        if (LionfishPrimaryModelLayerBridge.renderIfPresent(
                this,
                poseStack,
                vertexConsumer,
                packedLight,
                packedOverlay,
                red,
                green,
                blue,
                alpha)
                || LionfishSecondaryModelLayerBridge.renderIfPresent(
                this,
                poseStack,
                vertexConsumer,
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
