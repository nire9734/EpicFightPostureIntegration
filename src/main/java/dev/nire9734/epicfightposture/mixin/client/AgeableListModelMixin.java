package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.HumanoidModelPoseAdapter;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Final pose hook immediately before AgeableListModel submits its ModelParts.
 * This occurs after a third-party HumanoidModel subclass has completely returned
 * from setupAnim, so mod-specific post-super animation cannot overwrite EFPI's
 * BROKEN/EXECUTING pose.
 */
@Mixin(AgeableListModel.class)
public abstract class AgeableListModelMixin {
    @Inject(
            method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
            at = @At("HEAD")
    )
    private void efpi$applyFinalHumanoidPose(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha,
            CallbackInfo ci) {
        if ((Object)this instanceof HumanoidModel<?> humanoidModel) {
            HumanoidModelPoseAdapter.applyRemembered(humanoidModel);
        }
    }
}
