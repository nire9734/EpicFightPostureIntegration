package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.client.HumanoidModelPoseAdapter;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies EFPI's special-state HumanoidModel animation after the concrete model
 * has completed setupAnim(). The adapter may also transform the shared PoseStack,
 * which acts as EFPI's synthetic root bone and moves the whole model together.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererHumanoidMixin<T extends LivingEntity> {
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void efpi$applyHumanoidActionPose(
            T entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci) {
        LivingEntityRenderer<?, ?> renderer = (LivingEntityRenderer<?, ?>)(Object)this;
        EntityModel<?> activeModel = renderer.getModel();
        if (activeModel instanceof HumanoidModel<?> humanoidModel) {
            HumanoidModelPoseAdapter.apply(humanoidModel, entity, poseStack, partialTick);
        }
    }
}
