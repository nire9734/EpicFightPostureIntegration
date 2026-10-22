package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.compat.humanoid.HumanoidDiscoveryClientBridge;
import dev.nire9734.epicfightposture.compat.humanoid.HumanoidRenderState;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import dev.nire9734.epicfightposture.compat.spider.client.SpecialStateSpiderRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.SelfAccessor;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.HumanoidModelBaker;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Client-side renderer discovery and Epic Fight bridge registration. */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class VanillaHumanoidClientCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onAddPatchedRenderers(PatchedRenderersEvent.Add event) {
        HumanoidDiscoveryClientBridge.install(HumanoidClientDiscovery::handleServerRequest);
        HumanoidRenderState.installClientResolver((entityId, uuid) -> {
            ClientPostureStore.Entry state = ClientPostureStore.get(entityId, uuid);
            return state != null && state.broken;
        });

        HumanoidBackendRegistry.clear();
        HumanoidBackendRegistry.register(ModelPartHumanoidClientCompat.INSTANCE);
        if (ModList.get().isLoaded("geckolib")) {
            // The renderers (and their baked models) may have changed after F3+T.
            GeckoArmorSkinnedMeshBridge.clearCache();
            SpecialStateGeckoRenderer.clearFallbackCache();
            HumanoidBackendRegistry.register(GeckoHumanoidClientCompat.INSTANCE);
        }
        if (ModList.get().isLoaded("lionfishapi")) {
            HumanoidBackendRegistry.register(LionfishHumanoidClientCompat.INSTANCE);
        }
        // Plans and secondary meshes depend on the previous model/armature identity.
        LayerPoseOverrideContext.clearCache();
        LionfishSecondaryModelLayerBridge.clearCache();

        HumanoidClientDiscovery.ScanSnapshot scan = HumanoidClientDiscovery.scan(
                event.getContext().getEntityRenderDispatcher().renderers
        );

        Set<EntityType<?>> alreadyPatched = new HashSet<>(EntityPatchProvider.getPatchedEntities());
        Set<EntityType<?>> verifiedVanilla = registerVanillaHumanoids(event, scan, alreadyPatched);
        Set<EntityType<?>> verifiedSpider = registerVanillaSpiders(event, scan, alreadyPatched);
        Set<EntityType<?>> verifiedPreparedHumanoids = HumanoidBackendRegistry.registerRenderers(
                event,
                scan.extendedHumanoidTypes()
        );

        Set<ResourceLocation> verifiedVanillaIds = idsOf(verifiedVanilla);
        Set<ResourceLocation> verifiedPreparedIds = idsOf(verifiedPreparedHumanoids);
        Set<ResourceLocation> verifiedSpiderIds = idsOf(verifiedSpider);

        // Fail closed for EFPI-owned patches no longer supported by the latest
        // renderer scan, and recover patches whose renderer now registered safely.
        Set<ResourceLocation> verifiedAll = new HashSet<>(verifiedVanillaIds);
        verifiedAll.addAll(verifiedPreparedIds);
        verifiedAll.addAll(verifiedSpiderIds);
        VanillaHumanoidCompat.reconcileClientRuntimeCompatibility(verifiedAll);

        VanillaHumanoidCompat.replaceDetectedCache(
                verifiedVanillaIds,
                verifiedPreparedIds,
                verifiedSpiderIds
        );
        HumanoidClientDiscovery.updateServerReportSnapshot(new HumanoidClientDiscovery.ScanSnapshot(
                scan.inspected(),
                verifiedVanillaIds,
                verifiedPreparedIds,
                verifiedSpiderIds,
                verifiedVanilla,
                verifiedPreparedHumanoids,
                verifiedSpider
        ));

        LinkedHashMap<String, Integer> summary = new LinkedHashMap<>();
        if (!verifiedVanilla.isEmpty()) {
            summary.put("vanilla", verifiedVanilla.size());
        }
        for (Map.Entry<String, Integer> entry : HumanoidBackendRegistry.lastVerifiedByBackend().entrySet()) {
            summary.put(entry.getKey(), entry.getValue());
        }
        if (!verifiedSpider.isEmpty()) {
            summary.put("spider", verifiedSpider.size());
        }
        LOGGER.info(
                "EFPI compatibility: {} target(s) {}",
                verifiedVanillaIds.size() + verifiedPreparedIds.size() + verifiedSpiderIds.size(),
                summary
        );
    }

    private static Set<EntityType<?>> registerVanillaHumanoids(
            PatchedRenderersEvent.Add event,
            HumanoidClientDiscovery.ScanSnapshot scan,
            Set<EntityType<?>> alreadyPatched) {
        Set<EntityType<?>> verified = new HashSet<>();

        for (EntityType<?> entityType : scan.vanillaHumanoidTypes()) {
            ResourceLocation entityId = EntityType.getKey(entityType);
            if (entityId == null) {
                continue;
            }

            boolean oursAlready = VanillaHumanoidCompat.isRegisteredByEfpi(entityId);
            if (alreadyPatched.contains(entityType) && !oursAlready) {
                continue;
            }

            AssetAccessor<SkinnedMesh> runtimeMesh = bakeRuntimeMesh(event, entityType, entityId);
            if (runtimeMesh == null) {
                VanillaHumanoidCompat.rejectRuntimeCompatibility(entityType);
                continue;
            }

            if (!VanillaHumanoidCompat.registerDiscoveredAtRuntime(
                    entityType,
                    VanillaHumanoidCompat.CompatibilityKind.VANILLA_HUMANOID)) {
                continue;
            }

            if (!RendererRegistrationGuard.install(
                    () -> event.addPatchedEntityRenderer(
                            entityType,
                            type -> new SpecialStateHumanoidRenderer(event.getContext(), type, runtimeMesh)
                    ),
                    () -> VanillaHumanoidCompat.rejectRuntimeCompatibility(entityType),
                    throwable -> LOGGER.warn("EFPI could not install vanilla humanoid renderer for {}",
                            entityId, throwable))) {
                continue;
            }
            verified.add(entityType);
        }

        return Set.copyOf(verified);
    }

    private static Set<EntityType<?>> registerVanillaSpiders(
            PatchedRenderersEvent.Add event,
            HumanoidClientDiscovery.ScanSnapshot scan,
            Set<EntityType<?>> alreadyPatched) {
        Set<EntityType<?>> verified = new HashSet<>();

        for (EntityType<?> entityType : scan.vanillaSpiderTypes()) {
            ResourceLocation entityId = EntityType.getKey(entityType);
            if (entityId == null) {
                continue;
            }

            boolean oursAlready = VanillaHumanoidCompat.isRegisteredByEfpi(entityId);
            if (alreadyPatched.contains(entityType) && !oursAlready) {
                continue;
            }

            if (!VanillaHumanoidCompat.registerDiscoveredAtRuntime(
                    entityType,
                    VanillaHumanoidCompat.CompatibilityKind.VANILLA_SPIDER)) {
                continue;
            }

            if (!RendererRegistrationGuard.install(
                    () -> event.addPatchedEntityRenderer(
                            entityType,
                            type -> new SpecialStateSpiderRenderer(event.getContext(), type)
                    ),
                    () -> VanillaHumanoidCompat.rejectRuntimeCompatibility(entityType),
                    throwable -> LOGGER.warn("EFPI could not install vanilla spider renderer for {}",
                            entityId, throwable))) {
                continue;
            }
            verified.add(entityType);
        }

        return Set.copyOf(verified);
    }

    private static AssetAccessor<SkinnedMesh> bakeRuntimeMesh(
            PatchedRenderersEvent.Add event,
            EntityType<?> entityType,
            ResourceLocation entityId) {
        try {
            Object sourceRenderer = event.getContext().getEntityRenderDispatcher().renderers.get(entityType);
            if (!(sourceRenderer instanceof LivingEntityRenderer<?, ?> livingRenderer)
                    || !(livingRenderer.getModel() instanceof HumanoidModel<?> humanoidModel)) {
                LOGGER.debug(
                        "EFPI HumanoidModel target {} changed renderer/model before preparation; skipping automatic compatibility",
                        entityId
                );
                return null;
            }

            SkinnedMesh baked = RuntimeMeshOwnership.callOwned(
                    () -> HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(humanoidModel)
            );
            if (baked == null) {
                LOGGER.debug(
                        "EFPI could not transform HumanoidModel for {}; skipping automatic compatibility",
                        entityId
                );
                return null;
            }

            ResourceLocation runtimeMeshId = new ResourceLocation(
                    EpicFightPostureIntegration.MODID,
                    "runtime_humanoid/" + entityId.getNamespace() + "/" + entityId.getPath()
            );
            return SelfAccessor.create(runtimeMeshId, baked);
        } catch (Throwable throwable) {
            LOGGER.debug(
                    "EFPI failed to prepare HumanoidModel runtime mesh for {}; skipping automatic compatibility",
                    entityId,
                    throwable
            );
            return null;
        }
    }

    private static Set<ResourceLocation> idsOf(Set<EntityType<?>> types) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (EntityType<?> type : types) {
            ResourceLocation id = EntityType.getKey(type);
            if (id != null) {
                ids.add(id);
            }
        }
        return Set.copyOf(ids);
    }

    private VanillaHumanoidClientCompat() {
    }
}
