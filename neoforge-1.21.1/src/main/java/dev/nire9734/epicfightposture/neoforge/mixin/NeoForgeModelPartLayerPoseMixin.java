package dev.nire9734.epicfightposture.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.client.LayerPoseOverrideContext;
import dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeSpecialRenderHooks;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Source ModelPart layers may be owned by Lionfish renderers as well. */
@Mixin(ModelPart.class)
public abstract class NeoForgeModelPartLayerPoseMixin {
    @Inject(method = "translateAndRotate", at = @At("HEAD"), cancellable = true)
    private void efpi$useLayerPose(PoseStack poseStack, CallbackInfo ci) {
        if (NeoForgeSpecialRenderHooks.isLayerReplayActive()
                && LayerPoseOverrideContext.applyIfPresent(poseStack, this)) {
            ci.cancel();
        }
    }
}
