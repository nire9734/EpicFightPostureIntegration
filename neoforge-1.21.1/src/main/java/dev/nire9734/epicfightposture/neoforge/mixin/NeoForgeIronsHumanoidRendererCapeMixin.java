package dev.nire9734.epicfightposture.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeGeckoPostRenderMatrixOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

/** Gecko's ordinary per-bone layer pass does not cover Iron's post-super cape. */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.entity.mobs.HumanoidRenderer", remap = false)
public abstract class NeoForgeIronsHumanoidRendererCapeMixin {
    @Redirect(
            method = {"lambda$render$0", "lambda$render$1", "lambda$render$2"},
            at = @At(
                    value = "INVOKE",
                    target = "Lsoftware/bernie/geckolib/util/RenderUtil;prepMatrixForBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/cache/object/GeoBone;)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void efpi$useSpecialStateCapeMatrix(PoseStack stack, GeoBone bone) {
        if (!NeoForgeGeckoPostRenderMatrixOverride.applyIfPresent(stack, bone)) {
            RenderUtil.prepMatrixForBone(stack, bone);
        }
    }

    // The cape callback can be skipped for invisible entities. Do not leave an
    // armed bone in the render thread if the callback was not reached.
    @Inject(method = "render", at = @At("RETURN"), remap = false, require = 0)
    private void efpi$clearSpecialStateCapeMatrix(CallbackInfo ci) {
        NeoForgeGeckoPostRenderMatrixOverride.clear();
    }
}
