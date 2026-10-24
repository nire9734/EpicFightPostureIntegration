package dev.nire9734.epicfightposture.neoforge.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
import net.minecraft.client.renderer.entity.EntityRenderer;
import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.neoforge.mixin.NeoForgeEntityRenderDispatcherAccessor;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeHumanoidRenderGate;
import dev.nire9734.epicfightposture.compat.humanoid.client.RuntimeMeshOwnership;
import dev.nire9734.epicfightposture.neoforge.client.NeoForgeRendererDiscoveryClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.GiantMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.SelfAccessor;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;
import yesman.epicfight.api.client.event.types.registry.RegisterPatchedRenderersEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;
import dev.nire9734.epicfightposture.compat.humanoid.runtime.epicfight.HumanoidArmatureFactory;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.api.client.model.transformer.HumanoidModelBaker;

/**
 * First verified runtime HumanoidModel renderer for Epic Fight 21.17.
 * Registrations are client-only and never replace addon-owned EF renderers.
 */
@EventBusSubscriber(modid = "epicfight_posture_integration",
        bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NeoForgeHumanoidRendererRegistration {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForgeHumanoidRenderGate.installClientStateResolver((id, uuid) -> {
            ClientPostureStore.Entry state = ClientPostureStore.get(id, uuid);
            return state != null && state.broken;
        });
        EpicFightClientEventHooks.Registry.ADD_PATCHED_ENTITY.registerEvent(
                NeoForgeHumanoidRendererRegistration::onAddRenderers,
                "epicfight_posture_integration:humanoid_renderers",
                -10000
        );
    }

    private static void onAddRenderers(RegisterPatchedRenderersEvent.AddEntity event) {
        NeoForgeHumanoidRenderGate.startRendererReload();
        NeoForgeOptionalBackendBridge.onRendererReload();
        NeoForgeAutoCompatibility.beginClientRendererScan();

        Map<EntityType<?>, EntityRenderer<?>> renderers =
                ((NeoForgeEntityRenderDispatcherAccessor) (Object)
                        event.getContext().getEntityRenderDispatcher()).efpi$getRenderers();
        List<EntityType<?>> candidates = renderers.keySet().stream()
                .filter(type -> type != EntityType.PLAYER)
                .sorted(Comparator.comparing(type ->
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
                .toList();
        Map<ResourceLocation, String> coverage = new java.util.HashMap<>();
        Set<ResourceLocation> inspected = new HashSet<>();
        Set<ResourceLocation> vanillaIds = new HashSet<>();
        Set<ResourceLocation> extendedIds = new HashSet<>();
        Set<ResourceLocation> spiderIds = new HashSet<>();
        for (EntityType<?> type : candidates) {
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) continue;
            if (!NeoForgeAutoCompatibility.mayPrepareRenderer(type)) {
                coverage.put(id, "excluded:config");
                continue;
            }
            if (NeoForgeAutoCompatibility.isExternallyPatched(type)) {
                coverage.put(id, "excluded:existing_patch");
                continue;
            }
            EntityRenderer<?> source = renderers.get(type);
            boolean isGecko = NeoForgeOptionalBackendBridge.mightBeGeckoRenderer(source);
            if (!(source instanceof LivingEntityRenderer<?, ?>) && !isGecko) {
                coverage.put(id, "rejected:renderer_type");
                continue;
            }
            inspected.add(id);
            String backend = registerHumanoid(event, type);
            if (backend != null) {
                // The actual validated backend is authoritative. Inferring it
                // from the renderer's model superclass can mislabel a Mob and
                // conceal Forge/NeoForge coverage differences.
                coverage.put(id, "verified:" + backend);
                if ("spider".equals(backend)) spiderIds.add(id);
                else if ("vanilla".equals(backend)) vanillaIds.add(id);
                else extendedIds.add(id);
            } else {
                coverage.put(id, "rejected:backend_or_registration");
            }
        }
        NeoForgeRendererDiscoveryClient.record(inspected, vanillaIds, extendedIds, spiderIds);
        NeoForgeRendererCoverage.write(coverage);
        Map<String, Integer> verifiedByBackend = new TreeMap<>();
        for (String outcome : coverage.values()) {
            if (outcome.startsWith("verified:")) {
                verifiedByBackend.merge(outcome.substring("verified:".length()), 1, Integer::sum);
            }
        }
        LOGGER.info("EFPI NeoForge renderer scan: {} verified {} / {} inspected",
                verifiedByBackend.values().stream().mapToInt(Integer::intValue).sum(),
                verifiedByBackend, inspected.size());
        // F3+T may change model classifications without changing loaded MODs.
        // Ask the dedicated server for another trusted OP-only nonce handshake.
        NeoForgeRendererDiscoveryClient.requestServerRescanAfterReload();
    }

    /** Returns the verified renderer backend, or null if registration failed. */
    private static String registerHumanoid(RegisterPatchedRenderersEvent.AddEntity event,
                                            EntityType<?> type) {
        if (!NeoForgeAutoCompatibility.mayPrepareRenderer(type)
                || !rendererSlotIsFree(event, type)) return null;
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return null;
        try {
            EntityRenderer<?> sourceRenderer =
                    ((NeoForgeEntityRenderDispatcherAccessor) (Object)
                            event.getContext().getEntityRenderDispatcher()).efpi$getRenderers().get(type);
            if (sourceRenderer == null) return null;
            if (type == EntityType.GIANT && !(sourceRenderer instanceof GiantMobRenderer)) {
                LOGGER.warn("EFPI NeoForge Giant renderer changed; retaining original");
                return null;
            }

            if (sourceRenderer instanceof LivingEntityRenderer<?, ?> livingRenderer) {
                if (livingRenderer.getModel() instanceof SpiderModel<?>) {
                    if (!NeoForgeAutoCompatibility.registerVerifiedRuntimeSpider(type)) return null;
                    try {
                        event.addPatchedEntityRenderer(type,
                                unused -> new NeoForgeSpecialSpiderRenderer(event.getContext(), type));
                        NeoForgeHumanoidRenderGate.verified(type);
                        LOGGER.info("EFPI NeoForge verified SpiderModel renderer for {}", id);
                        return "spider";
                    } catch (RuntimeException exception) {
                        NeoForgeAutoCompatibility.rejectRuntimeCompatibility(type);
                        throw exception;
                    }
                }

                if (livingRenderer.getModel() instanceof HumanoidModel<?> model) {
                    SkinnedMesh mesh = RuntimeMeshOwnership.callOwned(() ->
                            HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(model));
                    if (mesh == null) return null;
                    AssetAccessor<SkinnedMesh> asset = SelfAccessor.create(
                            ResourceLocation.fromNamespaceAndPath("epicfight_posture_integration",
                                    "runtime_biped/" + id.getNamespace() + "/" + id.getPath()), mesh);
                    if (!NeoForgeAutoCompatibility.registerVerifiedRuntimeHumanoid(type)) return null;
                    try {
                        event.addPatchedEntityRenderer(type,
                                unused -> new NeoForgeSpecialHumanoidRenderer(event.getContext(), type, asset));
                        NeoForgeHumanoidRenderGate.verified(type);
                        LOGGER.info("EFPI NeoForge verified HumanoidModel renderer for {}", id);
                        return "vanilla";
                    } catch (RuntimeException exception) {
                        NeoForgeAutoCompatibility.rejectRuntimeCompatibility(type);
                        throw exception;
                    }
                }
            }

            // GeckoLib renderer does not inherit LivingEntityRenderer.
            // Lionfish uses AdvancedEntityModel instead of HumanoidModel.
            // Optional backend classes are loaded only when the dependency
            // exists. Unrecognized rigs retain their original rendering.
            NeoForgePreparedRig prepared = NeoForgeOptionalBackendBridge.inspect(type, sourceRenderer);
            if (prepared == null) return null;
            var skeleton = prepared.skeleton();
            var armature = HumanoidArmatureFactory.accessor(
                    ResourceLocation.fromNamespaceAndPath("epicfight_posture_integration",
                            "runtime_armature/" + id.getNamespace() + "/" + id.getPath()),
                    HumanoidArmatureFactory.create(
                            "efpi_runtime_" + id.getNamespace() + "_" + id.getPath(),
                            skeleton, Armatures.BIPED.get()));
            if (!NeoForgeAutoCompatibility.registerVerifiedRuntimeHumanoid(type, armature)) return null;
            try {
                event.addPatchedEntityRenderer(type,
                        unused -> new NeoForgeRiggedSpecialRenderer(prepared));
                NeoForgeHumanoidRenderGate.verified(type);
                LOGGER.info("EFPI NeoForge verified {} rigged renderer for {}", prepared.backend(), id);
                return prepared.backend();
            } catch (RuntimeException exception) {
                NeoForgeAutoCompatibility.rejectRuntimeCompatibility(type);
                throw exception;
            }
        } catch (RuntimeException exception) {
            NeoForgeAutoCompatibility.rejectRuntimeCompatibility(type);
            LOGGER.warn("EFPI NeoForge rejected renderer registration for {}", id, exception);
            return null;
        }
    }

    /**
     * Epic Fight 21.17's AddEntity API has no contains/get method, and
     * addPatchedEntityRenderer would silently overwrite another addon.
     * Reflection here is read-only; failure keeps vanilla rendering.
     */
    private static boolean rendererSlotIsFree(RegisterPatchedRenderersEvent.AddEntity event,
                                               EntityType<?> type) {
        try {
            Field providers = RegisterPatchedRenderersEvent.AddEntity.class
                    .getDeclaredField("entityRendererProvider");
            providers.setAccessible(true);
            Object raw = providers.get(event);
            if (!(raw instanceof Map<?, ?> map)) {
                LOGGER.warn("EFPI renderer provider ownership is unavailable; skipping compatibility rendering");
                return false;
            }
            if (map.containsKey(type)) {
                LOGGER.debug("EFPI renderer registration skipped {}: owned by Epic Fight or another addon", type);
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("EFPI could not verify Epic Fight renderer ownership; skipping compatibility rendering",
                    exception);
            return false;
        }
    }

    private NeoForgeHumanoidRendererRegistration() {}
}
