package dev.nire9734.epicfightposture.mixin.client;

import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/** Optional GeckoLib accessors used only while Gecko armor rendering is active. */
@Pseudo
@Mixin(targets = "software.bernie.geckolib.renderer.GeoArmorRenderer", remap = false)
public interface GeoArmorRendererInvoker {
    @Invoker("grabRelevantBones")
    void efpi$grabRelevantBones(BakedGeoModel bakedModel);

    @Invoker("applyBoneVisibilityBySlot")
    void efpi$applyBoneVisibilityBySlot(EquipmentSlot slot);
}
