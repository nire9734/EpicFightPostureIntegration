package dev.nire9734.epicfightposture.neoforge.compat.models;

import dev.nire9734.epicfightposture.compat.humanoid.client.GeckoHumanoidMeshAdapter;
import dev.nire9734.epicfightposture.compat.humanoid.client.RuntimeMeshOwnership;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import dev.nire9734.epicfightposture.neoforge.client.NeoForgePreparedRig;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yesman.epicfight.api.asset.SelfAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeLightweightHumanoidPatch;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;

/** GeckoLib 4.9.x optional backend: inspect static bind resources only. */
public final class NeoForgeGeckoBackend {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static NeoForgePreparedRig inspect(EntityType<?> type, EntityRenderer<?> source) {
        if (!(source instanceof GeoEntityRenderer<?> renderer)) return null;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return null;
        try {
            GeoModel<?> model = renderer.getGeoModel();
            if (model == null) return null;
            // Never instantiate a fake entity. Dynamic per-entity models are
            // skipped until a safe live-entity inspection strategy is available.
            ResourceLocation location = model.getModelResource(null);
            if (location == null) return null;
            BakedGeoModel baked = model.getBakedModel(location);
            if (baked == null || baked.topLevelBones() == null || baked.topLevelBones().isEmpty()) return null;
            GeckoHumanoidMeshAdapter.Result result = RuntimeMeshOwnership.callOwned(() ->
                    GeckoHumanoidMeshAdapter.analyseAndBake(
                            baked, new SkeletonKey(id.toString(), model.getClass().getName() + "|" + location),
                            GeckoBindPoseTransforms.OPERATIONS));
            // Forge 3.0.2 delegates acceptance to the semantic resolver and
            // mesh baker. Do not add a NeoForge-only confidence threshold.
            if (!result.supported() || result.descriptor() == null || result.mesh() == null) {
                LOGGER.debug("EFPI NeoForge Gecko humanoid rejected {}: {}", id, result.reason());
                return null;
            }
            LOGGER.debug("EFPI NeoForge Gecko humanoid accepted {}: confidence={}, virtualJoints={}",
                    id, result.descriptor().quality().overallConfidence(),
                    result.descriptor().quality().virtualJointCount());
            ResourceLocation meshId = ResourceLocation.fromNamespaceAndPath(
                    "epicfight_posture_integration", "runtime_gecko/" + id.getNamespace() + "/" + id.getPath());
            return new NeoForgePreparedRig(SelfAccessor.create(meshId, result.mesh()),
                    result.descriptor(), "geckolib");
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.debug("EFPI NeoForge Gecko humanoid inspection failed for {}", id, exception);
            return null;
        }
    }
    /**
     * Runtime-only GeckoLayer replay, called reflectively by the common renderer.
     * A different GeoModel or unsupported layer fails inside the optional bridge.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void renderAttachments(Mob entity, NeoForgeLightweightHumanoidPatch<Mob> patch,
                                         EntityRenderer<Mob> renderer, MultiBufferSource buffers,
                                         PoseStack stack, int light, int overlay, float partialTicks,
                                         HumanoidSkeletonDescriptor skeleton) {
        try (NeoForgeSpecialRenderHooks.GeckoArmorScope armorReplay =
                     NeoForgeSpecialRenderHooks.beginGeckoArmorReplay();
             NeoForgeGeckoArmorSkinnedMeshBridge.FrameScope armorFrame =
                     NeoForgeGeckoArmorSkinnedMeshBridge.openFrame(
                             entity, patch.getArmature(), skeleton, stack)) {
            NeoForgeGeckoAttachmentBridge.syncAndRenderLayers(entity,
                    (yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch) patch,
                    (EntityRenderer) renderer, buffers, stack, light, overlay, partialTicks);
        }
    }

    public static void clearArmorCaches() {
        NeoForgeGeckoArmorSkinnedMeshBridge.clearCache();
        NeoForgeGeckoPostRenderMatrixOverride.clear();
    }

    public static void armPostRenderTorso(Mob entity,
                                         NeoForgeLightweightHumanoidPatch<Mob> patch,
                                         EntityRenderer<Mob> renderer) {
        NeoForgeGeckoPostRenderMatrixOverride.armTorsoFromEpicFight(entity, patch, renderer);
    }

    private NeoForgeGeckoBackend() {}
}
