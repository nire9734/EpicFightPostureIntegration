package dev.nire9734.epicfightposture.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.neoforge.compat.models.LayerPoseOverrideContext;
import dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeSpecialRenderHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Project Lionfish attachments onto EF's verified skinned-pose joints. */
@Pseudo
@Mixin(targets = "com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox", remap = false)
public abstract class NeoForgeLionfishAdvancedModelBoxLayerPoseMixin {
    @Inject(method = "translateAndRotate", at = @At("HEAD"),
            cancellable = true, require = 0, remap = false)
    private void efpi$useLayerPose(PoseStack poseStack, CallbackInfo ci) {
        if (NeoForgeSpecialRenderHooks.isLayerReplayActive()
                && LayerPoseOverrideContext.applyIfPresent(poseStack, this)) {
            ci.cancel();
        }
    }
}
