package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Special-state renderer for GeckoLib-backed humanoids.
 *
 * The normal textured pass is always rendered. If Minecraft marks the entity as
 * glowing (for example while it is targeted), an outline pass is rendered in
 * addition to the textured pass instead of replacing it.
 *
 * Gecko's source bones and per-bone render layers are synchronized after the EF
 * body pass so held items and renderer-specific post-super attachments can
 * follow the same special-state pose. GeoArmorRenderer layers are intercepted
 * separately and converted to EF skinned meshes so clothing deforms with the
 * same Root/Chest and limb chains as the body.
 */
public final class SpecialStateGeckoRenderer extends PatchedEntityRenderer<
        LivingEntity,
        LivingEntityPatch<LivingEntity>,
        EntityRenderer<LivingEntity>,
        SkinnedMesh> {

    private final AssetAccessor<SkinnedMesh> mesh;
    private final HumanoidSkeletonDescriptor skeleton;

    public SpecialStateGeckoRenderer(
            AssetAccessor<SkinnedMesh> mesh,
            HumanoidSkeletonDescriptor skeleton,
            EntityRendererProvider.Context context) {
        this.mesh = mesh;
        this.skeleton = skeleton;
    }

    @Override
    public void render(
            LivingEntity entity,
            LivingEntityPatch<LivingEntity> entitypatch,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks) {
        super.render(entity, entitypatch, renderer, buffer, poseStack, packedLight, partialTicks);

        Minecraft mc = Minecraft.getInstance();
        ResourceLocation textureLocation = renderer.getTextureLocation(entity);
        Armature armature = entitypatch.getArmature();
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(entity.hurtTime > 5));

        poseStack.pushPose();

        // Epic Fight's GeckoLib compatibility invokes patched rendering from
        // GeoRenderEvent.Entity.Pre. GeckoLib fires that event after the source
        // renderer's preRender() hook, so renderer-side presentation transforms
        // (for example Iron's KeeperRenderer scale(1.3)) are already present on
        // this incoming PoseStack. Replaying preRender/scale here would apply the
        // presentation scale twice (1.3 * 1.3 for the Ancient Knight).
        this.mulPoseStack(poseStack, armature, entity, entitypatch, partialTicks);
        this.setArmaturePose(entitypatch, armature, partialTicks);

        SkinnedMesh runtimeMesh = this.mesh.get();

        float baseAlpha = entity.isInvisibleTo(mc.player) ? 0.15F : 1.0F;
        runtimeMesh.draw(
                poseStack,
                buffer,
                RenderType.entityCutoutNoCull(textureLocation),
                packedLight,
                1.0F,
                1.0F,
                1.0F,
                baseAlpha,
                overlay,
                armature,
                armature.getPoseMatrices()
        );

        if (mc.shouldEntityAppearGlowing(entity)) {
            runtimeMesh.draw(
                    poseStack,
                    buffer,
                    RenderType.outline(textureLocation),
                    packedLight,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    overlay,
                    armature,
                    armature.getPoseMatrices()
            );
        }

        // Gecko's body render is cancelled by Epic Fight while overrideRender()
        // is active, so GeoRenderLayer callbacks still need replay. The frame
        // supplies the exact same root PoseStack + armature that drew the body;
        // ItemArmorGeoLayer's mixin can therefore replace GeoArmorRenderer output
        // with an EF-skinned armor mesh without reverse-projecting source bones.
        try (GeckoArmorSkinnedMeshBridge.FrameScope ignored =
                     GeckoArmorSkinnedMeshBridge.openFrame(
                             entity,
                             armature,
                             this.skeleton,
                             poseStack)) {
            GeckoAttachmentBridge.syncAndRenderLayers(
                    entity,
                    entitypatch,
                    renderer,
                    buffer,
                    poseStack,
                    packedLight,
                    overlay,
                    partialTicks
            );
        }

        // Some Gecko renderers draw attachments after their super.render()
        // returns instead of using GeoRenderLayer. Queue the exact Chest skin
        // matrix + authored torso local bind for that immediate post-render call.
        GeckoPostRenderMatrixOverride.armTorsoFromEpicFight(entity, entitypatch, renderer);

        poseStack.popPose();
    }

    @Override
    public AssetAccessor<SkinnedMesh> getDefaultMesh() {
        return this.mesh;
    }
}
