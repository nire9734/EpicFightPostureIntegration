package dev.nire9734.epicfightposture.mixin.client;

import dev.nire9734.epicfightposture.compat.humanoid.client.HumanoidModelPoseAdapter;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records which living entity the shared HumanoidModel instance is currently
 * animating. The actual EFPI pose override is deferred until renderToBuffer so
 * subclasses remain free to add their own setupAnim changes first.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void efpi$rememberEntity(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
        HumanoidModelPoseAdapter.remember(model, entity);
    }
}
