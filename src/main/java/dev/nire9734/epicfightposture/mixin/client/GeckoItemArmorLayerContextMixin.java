package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoArmorBakeTransformContext;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoArmorSkinnedMeshBridge;
import dev.nire9734.epicfightposture.compat.humanoid.client.SpecialRenderHooks;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Converts one Gecko armor callback at a time. The original ItemArmorGeoLayer
 * decides which source bone, ModelPart and reported slot belong together; EFPI
 * only replaces that individual draw after reproducing GeckoLib's bind-space
 * fitting step.
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

    @Shadow
    @Nonnull
    protected abstract ModelPart getModelPartForBone(
            GeoBone bone,
            EquipmentSlot slot,
            ItemStack stack,
            LivingEntity animatable,
            HumanoidModel<?> baseModel);

    @Shadow
    protected abstract void prepModelPartForRender(
            PoseStack poseStack,
            GeoBone bone,
            ModelPart sourcePart);

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
        if (!SpecialRenderHooks.isGeckoArmorReplayActive()) {
            return;
        }

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
        if (!(armorModel instanceof GeoArmorRenderer<?>)) {
            return;
        }

        ModelPart armorPart = this.getModelPartForBone(
                bone,
                reportedSlot,
                armorStack,
                animatable,
                armorModel
        );
        if (armorPart == null || bone.getCubes() == null || bone.getCubes().isEmpty()) {
            return;
        }

        PoseStack fitStack = new PoseStack();
        float rotX = bone.getRotX();
        float rotY = bone.getRotY();
        float rotZ = bone.getRotZ();
        try {
            BoneSnapshot initial = bone.getInitialSnapshot();
            if (initial != null) {
                bone.setRotX(initial.getRotX());
                bone.setRotY(initial.getRotY());
                bone.setRotZ(initial.getRotZ());
            }
            this.prepModelPartForRender(fitStack, bone, armorPart);
        } catch (Throwable ignored) {
            return;
        } finally {
            bone.setRotX(rotX);
            bone.setRotY(rotY);
            bone.setRotZ(rotZ);
        }

        Vector3f fitScale = fitStack.last().pose().getScale(new Vector3f());
        boolean rendered;
        try (GeckoArmorBakeTransformContext.Scope ignored =
                     GeckoArmorBakeTransformContext.open(bone, fitScale)) {
            rendered = GeckoArmorSkinnedMeshBridge.renderIfSupported(
                    animatable,
                    bone,
                    armorStack,
                    reportedSlot,
                    armorModel,
                    armorPart,
                    fitScale,
                    bufferSource,
                    packedLight,
                    packedOverlay,
                    partialTick
            );
        }

        if (rendered) {
            ci.cancel();
        }
    }
}
