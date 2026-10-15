package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishPrimaryModelLayerBridge;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishRetargetDiagnostics;
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
            // BasicEntityModel overrides Minecraft's Model#renderToBuffer. Lionfish is
            // distributed against the SRG namespace, where this method is m_7695_.
            // Keep both selectors because @Pseudo targets the optional Lionfish class
            // without remapping its class name, while dev runs use the named method.
            method = {"renderToBuffer", "m_7695_"},
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
                alpha)) {
            ci.cancel();
            return;
        }
        if (LionfishSecondaryModelLayerBridge.renderIfPresent(
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
            return;
        }
        LionfishRetargetDiagnostics.sourceFallback(this, poseStack);
    }
}
