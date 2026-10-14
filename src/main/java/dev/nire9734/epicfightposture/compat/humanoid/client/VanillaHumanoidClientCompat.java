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
            HumanoidBackendRegistry.register(GeckoHumanoidClientCompat.INSTANCE);
        }
        if (ModList.get().isLoaded("lionfishapi")) {
            HumanoidBackendRegistry.register(LionfishHumanoidClientCompat.INSTANCE);
        }

        HumanoidClientDiscovery.ScanSnapshot scan = HumanoidClientDiscovery.scan(
                event.getContext().getEntityRenderDispatcher().renderers
        );

        Set<EntityType<?>> alreadyPatched = new HashSet<>(EntityPatchProvider.getPatchedEntities());
        Set<EntityType<?>> verifiedVanilla = registerVanillaHumanoids(event, scan, alreadyPatched);
        Set<EntityType<?>> verifiedSpider = registerVanillaSpiders(event, scan, alreadyPatched);
        Set<EntityType<?>> verifiedPreparedHumanoids = HumanoidBackendRegistry.registerRenderers(
                event,
                scan.externalHumanoidTypes()
        );

        Set<ResourceLocation> verifiedVanillaIds = idsOf(verifiedVanilla);
        Set<ResourceLocation> verifiedPreparedIds = idsOf(verifiedPreparedHumanoids);
        Set<ResourceLocation> verifiedSpiderIds = idsOf(verifiedSpider);

        // The dedicated-server protocol still uses the legacy GeckoLib field name for
        // the prepared humanoid bucket. Its semantics are backend-neutral.
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
                continue;
            }

            if (!oursAlready && !VanillaHumanoidCompat.registerDiscoveredAtRuntime(
                    entityType,
                    VanillaHumanoidCompat.CompatibilityKind.VANILLA_HUMANOID)) {
                continue;
            }

            event.addPatchedEntityRenderer(
                    entityType,
                    type -> new SpecialStateHumanoidRenderer(event.getContext(), type, runtimeMesh)
            );
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

            if (!oursAlready && !VanillaHumanoidCompat.registerDiscoveredAtRuntime(
                    entityType,
                    VanillaHumanoidCompat.CompatibilityKind.VANILLA_SPIDER)) {
                continue;
            }

            event.addPatchedEntityRenderer(
                    entityType,
                    type -> new SpecialStateSpiderRenderer(event.getContext(), type)
            );
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
                LOGGER.warn(
                        "Humanoid compatibility target {} no longer exposes HumanoidModel; skipping automatic compatibility",
                        entityId
                );
                return null;
            }

            SkinnedMesh baked = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(humanoidModel);
            if (baked == null) {
                LOGGER.warn(
                        "Epic Fight could not transform HumanoidModel for {}; skipping automatic compatibility",
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
            LOGGER.warn(
                    "Failed to prepare Epic Fight runtime mesh for {}; skipping automatic compatibility",
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
