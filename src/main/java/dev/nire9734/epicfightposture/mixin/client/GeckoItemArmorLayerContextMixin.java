package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoArmorSkinnedMeshBridge;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Replaces supported GeoArmorRenderer callbacks with a single EF-skinned armor
 * mesh draw. Vanilla/non-Geo armor and unsupported custom renderers fall through
 * to GeckoLib unchanged.
 */
@Pseudo
@Mixin(targets = "software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer", remap = false)
public abstract class GeckoItemArmorLayerContextMixin {
    @Shadow
    @Nullable
    protected abstract ItemStack getArmorItemForBone(GeoBone bone, LivingEntity animatable);

    @Shadow
    @Nonnull
    protected abstract EquipmentSlot getEquipmentSlotForBone(
            GeoBone bone,
            ItemStack stack,
            LivingEntity animatable);

    @Shadow
    @Nonnull
    protected abstract HumanoidModel<?> getModelForItem(
            GeoBone bone,
            EquipmentSlot slot,
            ItemStack stack,
            LivingEntity animatable);

    @Inject(
            method = "renderForBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/LivingEntity;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;FII)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void efpi$renderSkinnedArmor(
            PoseStack poseStack,
            LivingEntity animatable,
            GeoBone bone,
            RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer,
            float partialTick,
            int packedLight,
            int packedOverlay,
            CallbackInfo ci) {
        ItemStack armorStack = this.getArmorItemForBone(bone, animatable);
        if (armorStack == null || armorStack.isEmpty()) {
            return;
        }

        EquipmentSlot reportedSlot = this.getEquipmentSlotForBone(
                bone,
                armorStack,
                animatable
        );
        HumanoidModel<?> armorModel = this.getModelForItem(
                bone,
                reportedSlot,
                armorStack,
                animatable
        );

        if (GeckoArmorSkinnedMeshBridge.renderIfSupported(
                animatable,
                armorStack,
                reportedSlot,
                armorModel,
                bufferSource,
                packedLight,
                packedOverlay,
                partialTick)) {
            ci.cancel();
        }
    }
}
