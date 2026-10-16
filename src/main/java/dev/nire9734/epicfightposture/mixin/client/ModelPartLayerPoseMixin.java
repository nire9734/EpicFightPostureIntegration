package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.client.FrameSpikeDiagnostics;
import dev.nire9734.epicfightposture.compat.humanoid.client.LayerPoseOverrideContext;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies EFPI's exact source-layer pose only while a replay scope is active. */
@Mixin(ModelPart.class)
public abstract class ModelPartLayerPoseMixin {
    @Inject(method = "translateAndRotate", at = @At("HEAD"), cancellable = true)
    private void efpi$useLayerPose(PoseStack poseStack, CallbackInfo ci) {
        boolean overridden = LayerPoseOverrideContext.applyIfPresent(poseStack, this);
        FrameSpikeDiagnostics.recordModelPartHook(overridden);
        if (overridden) {
            ci.cancel();
        }
    }
}
