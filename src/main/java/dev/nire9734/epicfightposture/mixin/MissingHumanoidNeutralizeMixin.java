package dev.nire9734.epicfightposture.mixin;

import dev.nire9734.epicfightposture.compat.humanoid.MissingHumanoidNeutralize;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.CustomHumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

/**
 * Generic fix for CustomHumanoidMobPatch instances with a missing neutralize
 * animation entry. Does not overwrite datapack-provided animations, replace
 * the original patch, modify its AI, or target any specific mod/entity IDs.
 */
@Mixin(value = CustomHumanoidMobPatch.class, remap = false)
public abstract class MissingHumanoidNeutralizeMixin {
    @Inject(method = "getHitAnimation", at = @At("RETURN"), cancellable = true)
    private void efpi$supplyMissingNeutralize(
            StunType stunType,
            CallbackInfoReturnable<AssetAccessor<? extends StaticAnimation>> cir) {
        AssetAccessor<? extends StaticAnimation> fallback =
                MissingHumanoidNeutralize.resolve(
                        (LivingEntityPatch<?>) (Object) this,
                        stunType,
                        cir.getReturnValue());
        if (fallback != null) {
            cir.setReturnValue(fallback);
        }
    }
}
