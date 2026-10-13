package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoPostRenderMatrixOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Iron's Spells renders its humanoid cape outside GeckoLib's GeoRenderLayer
 * pipeline. HumanoidRenderer.render() resumes after Gecko's cancelled render,
 * looks up the live "torso" bone and calls prepMatrixForBone from a synthetic
 * lambda before rendering ArmorCapeLayer.
 *
 * <p>When EFPI has an exact EF skin/bind matrix queued for that same bone,
 * consume it here instead of rebuilding the pose from mutable GeoBone Euler
 * values. If no override is armed this is a strict no-op compatibility mixin
 * and GeckoLib receives its original call unchanged.</p>
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.entity.mobs.HumanoidRenderer", remap = false)
public abstract class IronsHumanoidRendererCapeMixin {
    @Redirect(
            method = "lambda$render$1",
            at = @At(
                    value = "INVOKE",
                    target = "Lsoftware/bernie/geckolib/util/RenderUtils;prepMatrixForBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/core/animatable/model/CoreGeoBone;)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void efpi$useExactPostRenderMatrix(PoseStack poseStack, CoreGeoBone bone) {
        if (!GeckoPostRenderMatrixOverride.applyIfPresent(poseStack, bone)) {
            RenderUtils.prepMatrixForBone(poseStack, bone);
        }
    }
}
