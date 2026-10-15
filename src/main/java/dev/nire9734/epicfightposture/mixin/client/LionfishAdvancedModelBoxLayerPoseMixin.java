package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.client.LayerPoseOverrideContext;
import dev.nire9734.epicfightposture.compat.humanoid.client.LionfishRetargetDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional Lionfish equivalent of the vanilla ModelPart layer-pose hook. */
@Pseudo
@Mixin(targets = "com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox", remap = false)
public abstract class LionfishAdvancedModelBoxLayerPoseMixin {
    @Inject(
            method = "translateAndRotate",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void efpi$useLayerPose(PoseStack poseStack, CallbackInfo ci) {
        boolean applied = LayerPoseOverrideContext.applyIfPresent(poseStack, this);
        LionfishRetargetDiagnostics.partOverride(this, applied);
        if (applied) {
            ci.cancel();
        }
    }
}
