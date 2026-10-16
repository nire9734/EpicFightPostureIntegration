package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.LightweightHumanoidPatch;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.SelfAccessor;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.HumanoidModelBaker;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.client.renderer.patched.entity.PatchedEntityRenderer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

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
@SuppressWarnings({"rawtypes", "unchecked"})
public final class SpecialStateGeckoRenderer extends PatchedEntityRenderer<
        LivingEntity,
        LivingEntityPatch<LivingEntity>,
        EntityRenderer<LivingEntity>,
        SkinnedMesh> {

    private static final Map<HumanoidModel<?>, Map<EntityType<?>, SpecialStateHumanoidRenderer>> HUMANOID_FALLBACKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final AssetAccessor<SkinnedMesh> mesh;
    private final HumanoidSkeletonDescriptor skeleton;
    private final EntityRendererProvider.Context context;

    public SpecialStateGeckoRenderer(
            AssetAccessor<SkinnedMesh> mesh,
            HumanoidSkeletonDescriptor skeleton,
            EntityRendererProvider.Context context) {
        this.mesh = mesh;
        this.skeleton = skeleton;
        this.context = context;
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
        if (renderHumanoidDelegateIfPresent(
                entity,
                entitypatch,
                renderer,
                buffer,
                poseStack,
                packedLight,
                partialTicks)) {
            return;
        }

        super.render(entity, entitypatch, renderer, buffer, poseStack, packedLight, partialTicks);

        Minecraft mc = Minecraft.getInstance();
        ResourceLocation textureLocation = renderer.getTextureLocation(entity);
        Armature armature = entitypatch.getArmature();
        int overlay = OverlayTexture.pack(0, OverlayTexture.v(entity.hurtTime > 5));

        poseStack.pushPose();
        try {
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

            SpecialRenderHooks.beginGeckoArmorReplay();
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
            } finally {
                SpecialRenderHooks.endGeckoArmorReplay();
            }

            GeckoPostRenderMatrixOverride.armTorsoFromEpicFight(entity, entitypatch, renderer);
        } finally {
            poseStack.popPose();
        }
    }

    private boolean renderHumanoidDelegateIfPresent(
            LivingEntity entity,
            LivingEntityPatch<LivingEntity> entitypatch,
            EntityRenderer<LivingEntity> renderer,
            MultiBufferSource buffer,
            PoseStack poseStack,
            int packedLight,
            float partialTicks) {
        if (!(entitypatch instanceof LightweightHumanoidPatch lightweightPatch)
                || !(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)
                || !(livingRenderer.getModel() instanceof HumanoidModel<?> humanoidModel)) {
            return false;
        }

        SpecialStateHumanoidRenderer fallback = humanoidFallback(entity, humanoidModel);
        if (fallback == null) {
            return false;
        }

        fallback.render(
                entity,
                lightweightPatch,
                (LivingEntityRenderer) livingRenderer,
                buffer,
                poseStack,
                packedLight,
                partialTicks
        );
        return true;
    }

    private SpecialStateHumanoidRenderer humanoidFallback(
            LivingEntity entity,
            HumanoidModel<?> humanoidModel) {
        synchronized (HUMANOID_FALLBACKS) {
            EntityType<?> entityType = entity.getType();
            Map<EntityType<?>, SpecialStateHumanoidRenderer> byType =
                    HUMANOID_FALLBACKS.computeIfAbsent(humanoidModel, ignored -> new HashMap<>());
            SpecialStateHumanoidRenderer cached = byType.get(entityType);
            if (cached != null) {
                return cached;
            }

            try {
                SkinnedMesh baked = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(humanoidModel);
                if (baked == null) {
                    return null;
                }

                ResourceLocation entityId = EntityType.getKey(entityType);
                String namespace = entityId == null ? EpicFightPostureIntegration.MODID : entityId.getNamespace();
                String path = entityId == null ? "unknown" : entityId.getPath();
                ResourceLocation runtimeMeshId = new ResourceLocation(
                        EpicFightPostureIntegration.MODID,
                        "runtime_hybrid/" + namespace + "/" + path + "/"
                                + Integer.toHexString(System.identityHashCode(humanoidModel))
                );
                AssetAccessor<SkinnedMesh> accessor = SelfAccessor.create(runtimeMeshId, baked);
                SpecialStateHumanoidRenderer created = new SpecialStateHumanoidRenderer(
                        this.context,
                        entityType,
                        accessor
                );
                byType.put(entityType, created);
                return created;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    @Override
    public AssetAccessor<SkinnedMesh> getDefaultMesh() {
        return this.mesh;
    }
}
