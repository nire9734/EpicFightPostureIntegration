package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoArmorSkinnedMeshCore;
import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoBindPoseOperations;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.neoforge.mixin.NeoForgeGeoArmorRendererInvoker;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import yesman.epicfight.api.model.Armature;

/** GeckoLib 4.9.x API adapter for the shared armor skinning engine. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class NeoForgeGeckoArmorSkinnedMeshBridge {
    private static final GeckoArmorSkinnedMeshCore.Platform PLATFORM = new GeckoArmorSkinnedMeshCore.Platform() {
        @Override
        public GeckoArmorSkinnedMeshCore.PreparedArmorBase prepareArmor(
                GeoArmorRenderer<?> renderer, LivingEntity entity, ItemStack stack,
                EquipmentSlot slot, HumanoidModel<?> armorModel, ModelPart armorPart,
                MultiBufferSource buffer, float partialTick) {
            try {
                GeoArmorRenderer raw = (GeoArmorRenderer) renderer;
                raw.prepForRender(entity, stack, slot, armorModel);
                GeoAnimatable animatable = (GeoAnimatable) raw.getAnimatable();
                GeoModel model = (GeoModel) raw.getGeoModel();
                GeoRenderer geoRenderer = (GeoRenderer) raw;
                ResourceLocation location = model.getModelResource(animatable);
                BakedGeoModel baked = model.getBakedModel(location);
                if (baked == null || baked.topLevelBones() == null
                        || !(renderer instanceof NeoForgeGeoArmorRendererInvoker invoker)) return null;

                GeckoBindPoseTransforms.resetToInitial(baked);
                invoker.efpi$applyBaseModel(armorModel);
                invoker.efpi$grabRelevantBones(baked);
                invoker.efpi$applyBaseTransformations(armorModel);
                renderer.applyBoneVisibilityByPart(slot, armorPart, armorModel);

                ResourceLocation texture = geoRenderer.getTextureLocation(animatable);
                RenderType type = geoRenderer.getRenderType(animatable, texture, buffer, partialTick);
                if (type == null) type = RenderType.armorCutoutNoCull(texture);
                return new GeckoArmorSkinnedMeshCore.PreparedArmorBase(baked, texture, type);
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public GeoBone[] regionBones(GeoArmorRenderer<?> renderer) {
            GeoModel model = (GeoModel) renderer.getGeoModel();
            return new GeoBone[]{renderer.getHeadBone(model), renderer.getBodyBone(model),
                    renderer.getRightArmBone(model), renderer.getLeftArmBone(model),
                    renderer.getRightLegBone(model), renderer.getLeftLegBone(model),
                    renderer.getRightBootBone(model), renderer.getLeftBootBone(model)};
        }

        @Override
        public boolean isArmorSlot(EquipmentSlot slot) {
            return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        }

        @Override
        public GeckoBindPoseOperations poseOperations() {
            return GeckoBindPoseTransforms.OPERATIONS;
        }
    };

    public static void clearCache() {
        GeckoArmorSkinnedMeshCore.clearCache();
    }

    public static FrameScope openFrame(LivingEntity entity, Armature armature,
                                       HumanoidSkeletonDescriptor skeleton, PoseStack bodyPoseStack) {
        return new FrameScope(GeckoArmorSkinnedMeshCore.openFrame(
                entity, armature, skeleton, bodyPoseStack));
    }

    public static boolean renderIfSupported(LivingEntity entity, GeoBone sourceBone,
                                            ItemStack stack, EquipmentSlot slot,
                                            HumanoidModel<?> armorModel, ModelPart armorPart,
                                            Vector3f fitScale, MultiBufferSource buffer,
                                            int packedLight, int packedOverlay, float partialTick) {
        return GeckoArmorSkinnedMeshCore.renderIfSupported(entity, sourceBone, stack,
                slot, armorModel, armorPart, fitScale, buffer, packedLight, packedOverlay,
                partialTick, PLATFORM);
    }

    public record FrameScope(GeckoArmorSkinnedMeshCore.FrameScope delegate) implements AutoCloseable {
        @Override public void close() { delegate.close(); }
    }

    private NeoForgeGeckoArmorSkinnedMeshBridge() { }
}
