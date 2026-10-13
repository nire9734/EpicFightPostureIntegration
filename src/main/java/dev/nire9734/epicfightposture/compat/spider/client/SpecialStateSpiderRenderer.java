package dev.nire9734.epicfightposture.compat.spider.client;

import dev.nire9734.epicfightposture.compat.spider.LightweightSpiderPatch;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.client.mesh.SpiderMesh;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

/**
 * Epic Fight special-state renderer for auto-detected Minecraft SpiderModel mobs.
 * Normal gameplay continues to use the source mod's renderer.
 */
public final class SpecialStateSpiderRenderer extends PatchedLivingEntityRenderer<
        LivingEntity,
        LightweightSpiderPatch,
        SpiderModel<LivingEntity>,
        LivingEntityRenderer<LivingEntity, SpiderModel<LivingEntity>>,
        SpiderMesh> {

    public SpecialStateSpiderRenderer(EntityRendererProvider.Context context, EntityType<?> entityType) {
        super(context, entityType);
    }

    @Override
    public AssetAccessor<SpiderMesh> getDefaultMesh() {
        return Meshes.SPIDER;
    }
}
