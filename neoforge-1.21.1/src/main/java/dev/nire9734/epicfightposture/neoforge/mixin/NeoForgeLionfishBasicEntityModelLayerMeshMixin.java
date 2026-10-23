package dev.nire9734.epicfightposture.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishPrimaryModelLayerBridge;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishSecondaryModelLayerBridge;
import dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeSpecialRenderHooks;
import dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeLayerColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lionfish 3.1 / Minecraft 1.21.1 uses one ARGB int, not Forge 1.20.1's
 * four RGBA floats. Only intercept render calls during EFPI layer replay.
 */
@Pseudo
@Mixin(targets = "com.github.L_Ender.lionfishapi.client.model.tools.BasicEntityModel", remap = false)
public abstract class NeoForgeLionfishBasicEntityModelLayerMeshMixin {
    @Inject(
            method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false
    )
    private void efpi$reuseSkinnedLayerMesh(
            PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight,
            int packedOverlay, int color, CallbackInfo ci) {
        if (!NeoForgeSpecialRenderHooks.isLayerReplayActive()) return;
        NeoForgeLayerColor argb = NeoForgeLayerColor.fromArgb(color);

        if (LionfishPrimaryModelLayerBridge.renderIfPresent(
                this, poseStack, vertexConsumer, packedLight, packedOverlay,
                argb.red(), argb.green(), argb.blue(), argb.alpha())
                || LionfishSecondaryModelLayerBridge.renderIfPresent(
                        this, poseStack, vertexConsumer, packedLight, packedOverlay,
                        argb.red(), argb.green(), argb.blue(), argb.alpha())) {
            ci.cancel();
        }
    }
}
