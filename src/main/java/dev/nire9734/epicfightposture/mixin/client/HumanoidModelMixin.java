package dev.nire9734.epicfightposture.mixin.client;

import dev.nire9734.epicfightposture.compat.humanoid.client.HumanoidModelPoseAdapter;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies EFPI's special-state pose after vanilla HumanoidModel has generated
 * its normal pose. HumanoidModel subclasses that call super.setupAnim are
 * covered automatically; models with a fully independent animation pipeline
 * can use a dedicated adapter later.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void efpi$applyActionPose(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
        HumanoidModelPoseAdapter.apply(model, entity);
    }
}
