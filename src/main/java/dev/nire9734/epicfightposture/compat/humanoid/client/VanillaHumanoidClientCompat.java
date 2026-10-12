package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.compat.humanoid.HumanoidDiscoveryClientBridge;
import dev.nire9734.epicfightposture.compat.humanoid.HumanoidRenderState;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
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
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.client.model.transformer.HumanoidModelBaker;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.util.HashSet;
import java.util.Set;

/** Client-side humanoid discovery and Epic Fight bridge registration. */
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

        boolean geckoLoaded = ModList.get().isLoaded("geckolib");
        if (geckoLoaded) {
            // The class itself contains direct GeckoLib references, so only resolve
            // it after Forge has confirmed that GeckoLib is actually installed.
            GeckoHumanoidClientCompat.installDiscovery();
        } else {
            HumanoidClientDiscovery.installAdditionalScanner(null);
        }

        HumanoidClientDiscovery.ScanSnapshot scan = HumanoidClientDiscovery.scan(
                event.getContext().getEntityRenderDispatcher().renderers
        );
        VanillaHumanoidCompat.replaceDetectedCache(scan.humanoid());

        Set<EntityType<?>> alreadyPatched = new HashSet<>(EntityPatchProvider.getPatchedEntities());
        for (EntityType<?> entityType : scan.vanillaHumanoidTypes()) {
            ResourceLocation entityId = EntityType.getKey(entityType);
            if (entityId == null) {
                continue;
            }

            boolean oursAlready = VanillaHumanoidCompat.isRegisteredByEfpi(entityId);
            if (alreadyPatched.contains(entityType) && !oursAlready) {
                // Never replace an Epic Fight patch supplied by Epic Fight itself,
                // another compatibility mod, or an earlier registry subscriber.
                continue;
            }

            if (!oursAlready && !VanillaHumanoidCompat.registerDiscoveredAtRuntime(entityType)) {
                continue;
            }

            // Convert the source HumanoidModel once during renderer registration.
            // The conversion itself is Epic Fight's VanillaModelTransformer; EFPI
            // only keeps the resulting runtime asset and wires it to this entity type.
            AssetAccessor<SkinnedMesh> runtimeMesh = bakeRuntimeMesh(event, entityType, entityId);

            event.addPatchedEntityRenderer(
                    entityType,
                    type -> new SpecialStateHumanoidRenderer(event.getContext(), type, runtimeMesh)
            );
        }

        if (geckoLoaded) {
            GeckoHumanoidClientCompat.registerRenderers(event, scan.externalHumanoidTypes());
        }

        LOGGER.info(
                "EFPI auto-detected {} compatible humanoid entity type(s): {} vanilla, {} external",
                scan.humanoid().size(),
                scan.vanillaHumanoidTypes().size(),
                scan.externalHumanoidTypes().size()
        );
    }

    private static AssetAccessor<SkinnedMesh> bakeRuntimeMesh(
            PatchedRenderersEvent.Add event,
            EntityType<?> entityType,
            ResourceLocation entityId) {
        try {
            Object sourceRenderer = event.getContext().getEntityRenderDispatcher().renderers.get(entityType);
            if (!(sourceRenderer instanceof LivingEntityRenderer<?, ?> livingRenderer)
                    || !(livingRenderer.getModel() instanceof HumanoidModel<?> humanoidModel)) {
                LOGGER.warn("Humanoid compatibility target {} no longer exposes HumanoidModel; using EF BIPED", entityId);
                return fallbackMesh();
            }

            SkinnedMesh baked = HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(humanoidModel);
            if (baked == null) {
                LOGGER.warn("Epic Fight could not transform HumanoidModel for {}; using EF BIPED", entityId);
                return fallbackMesh();
            }

            ResourceLocation runtimeMeshId = new ResourceLocation(
                    EpicFightPostureIntegration.MODID,
                    "runtime_humanoid/" + entityId.getNamespace() + "/" + entityId.getPath()
            );
            return SelfAccessor.create(runtimeMeshId, baked);
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to prepare Epic Fight runtime mesh for {}; using EF BIPED", entityId, throwable);
            return fallbackMesh();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AssetAccessor<SkinnedMesh> fallbackMesh() {
        return (AssetAccessor<SkinnedMesh>) (AssetAccessor) Meshes.BIPED;
    }

    private VanillaHumanoidClientCompat() {
    }
}
