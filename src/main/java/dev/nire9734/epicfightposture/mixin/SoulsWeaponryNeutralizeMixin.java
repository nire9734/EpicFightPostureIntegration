package dev.nire9734.epicfightposture.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.CustomHumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

/**
 * Supplies only the missing neutralize entry for the Soul's Weapons remnant
 * family when an existing Epic Fight datapack owns the CustomHumanoidMobPatch.
 *
 * Unlike replacing this patch with EFPI's lightweight bridge, this preserves
 * the owner's combat AI, all configured animations, attributes and item logic.
 * Entries explicitly supplied by a datapack take priority.
 */
@Mixin(value = CustomHumanoidMobPatch.class, remap = false)
public abstract class SoulsWeaponryNeutralizeMixin {
    private static final ResourceLocation REMNANT = new ResourceLocation("soulsweapons", "remnant");
    private static final ResourceLocation FORLORN = new ResourceLocation("soulsweapons", "forlorn");

    @Inject(method = "getHitAnimation", at = @At("RETURN"), cancellable = true)
    private void efpi$missingRemnantNeutralize(
            StunType stunType,
            CallbackInfoReturnable<AssetAccessor<? extends StaticAnimation>> cir) {
        if (stunType != StunType.NEUTRALIZE
                || (cir.getReturnValue() != null && !cir.getReturnValue().isEmpty())) {
            return;
        }

        LivingEntityPatch<?> patch = (LivingEntityPatch<?>) (Object) this;
        LivingEntity entity = patch.getOriginal();
        if (entity == null) {
            return;
        }

        ResourceLocation id = EntityType.getKey(entity.getType());
        if ((REMNANT.equals(id) || FORLORN.equals(id))
                && Animations.BIPED_COMMON_NEUTRALIZED != null
                && !Animations.BIPED_COMMON_NEUTRALIZED.isEmpty()) {
            cir.setReturnValue(Animations.BIPED_COMMON_NEUTRALIZED);
        }
    }
}
