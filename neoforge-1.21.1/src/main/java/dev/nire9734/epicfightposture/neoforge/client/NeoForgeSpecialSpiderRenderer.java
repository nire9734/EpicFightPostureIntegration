package dev.nire9734.epicfightposture.neoforge.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeLightweightSpiderPatch;
import dev.nire9734.epicfightposture.neoforge.mixin.NeoForgeLivingRendererScaleInvoker;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.mesh.SpiderMesh;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
/** Epic Fight special-state spider animation, source scale preserved. */
public final class NeoForgeSpecialSpiderRenderer extends PatchedLivingEntityRenderer<
        LivingEntity, NeoForgeLightweightSpiderPatch<LivingEntity>, SpiderModel<LivingEntity>,
        LivingEntityRenderer<LivingEntity, SpiderModel<LivingEntity>>, SpiderMesh> {
    private LivingEntityRenderer<LivingEntity, SpiderModel<LivingEntity>> source;
    public NeoForgeSpecialSpiderRenderer(EntityRendererProvider.Context context, EntityType<?> type) {
        super(context, type);
    }
    @Override public void render(LivingEntity entity, NeoForgeLightweightSpiderPatch<LivingEntity> patch,
                       LivingEntityRenderer<LivingEntity, SpiderModel<LivingEntity>> renderer,
                       MultiBufferSource buffer, PoseStack pose, int light, float ticks) {
        source = renderer;
        try { super.render(entity, patch, renderer, buffer, pose, light, ticks); }
        finally { source = null; }
    }
    @Override public void mulPoseStack(PoseStack pose, Armature armature, LivingEntity entity,
                             NeoForgeLightweightSpiderPatch<LivingEntity> patch, float ticks) {
        super.mulPoseStack(pose, armature, entity, patch, ticks);
        if (source != null) {
            ((NeoForgeLivingRendererScaleInvoker) (Object) source).efpi$invokeScale(entity, pose, ticks);
        }
    }
    @Override public AssetAccessor<SpiderMesh> getDefaultMesh() { return Meshes.SPIDER; }
}
