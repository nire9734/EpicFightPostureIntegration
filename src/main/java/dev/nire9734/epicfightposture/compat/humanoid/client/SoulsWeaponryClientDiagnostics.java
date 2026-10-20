package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.PostureManager;
import dev.nire9734.epicfightposture.SoulsWeaponryDiagnostics;
import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.shelmarow.combat_evolution.execution.ExecutionHandler;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.HashSet;
import java.util.Set;

/**
 * Passive client diagnostics: records both scan-time preparation and one live
 * entity sample per Soul's Weapons type, including failures before any HUD exists.
 */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class SoulsWeaponryClientDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> SAMPLED_LIVE_TYPES = new HashSet<>();

    public static void onRendererScan(HumanoidClientDiscovery.ScanSnapshot scan,
                                      Set<ResourceLocation> verified) {
        for (ResourceLocation id : SoulsWeaponryDiagnostics.TARGETS) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (type == null) {
                continue;
            }
            LOGGER.info(
                    "EFPI souls-diagnostic/scan: entity={}, inspected={}, vanillaHumanoid={}, " +
                    "extendedHumanoid={}, prepared={}, verifiedRenderer={}, " +
                    "efpiOwned={}, externallyPatched={}, autoCompatEnabled={}",
                    id, scan.inspected().contains(id), scan.vanillaHumanoid().contains(id),
                    scan.extendedHumanoid().contains(id),
                    HumanoidBackendRegistry.prepared(type) != null,
                    verified.contains(id),
                    VanillaHumanoidCompat.isRegisteredByEfpi(id),
                    VanillaHumanoidCompat.isExternallyPatched(id),
                    VanillaHumanoidCompat.isCompatibilityEnabled(id)
            );
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            SAMPLED_LIVE_TYPES.clear();
            return;
        }

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity.tickCount < 20) {
                continue;
            }
            ResourceLocation id = EntityType.getKey(living.getType());
            if (!SoulsWeaponryDiagnostics.isTarget(id) || !SAMPLED_LIVE_TYPES.add(id)) {
                continue;
            }
            try {
                LivingEntityPatch<?> patch =
                        EpicFightCapabilities.getEntityPatch(living, LivingEntityPatch.class);
                LivingEntityPatch<?> executor =
                        EpicFightCapabilities.getEntityPatch(mc.player, LivingEntityPatch.class);
                boolean neutralize = PostureManager.hasNeutralizeAnimation(patch);
                Boolean executionSupported = null;
                if (patch != null && executor != null) {
                    try {
                        executionSupported = ExecutionHandler.isTargetSupported(executor, patch);
                    } catch (Throwable ex) {
                        LOGGER.warn("EFPI souls-diagnostic/client execution support probe failed for {}",
                                id, ex);
                    }
                }
                var renderer = mc.getEntityRenderDispatcher().getRenderer(living);
                String model = renderer instanceof LivingEntityRenderer<?, ?> livingRenderer
                        ? livingRenderer.getModel().getClass().getName() : "not-living-renderer";
                var posture = ClientPostureStore.get(entity.getId(), entity.getUUID());
                LOGGER.info(
                        "EFPI souls-diagnostic/client: entity={}, patch={}, renderer={}, model={}, " +
                        "vanillaHumanoidModel={}, efpiOwned={}, autoCompatEnabled={}, " +
                        "neutralize={}, humanoidArmature={}, executeSupported={}, " +
                        "hasSyncedPosture={}, overrideRender={}",
                        id, patch == null ? "none" : patch.getClass().getName(),
                        renderer.getClass().getName(), model,
                        renderer instanceof LivingEntityRenderer<?, ?> lr
                                && lr.getModel() instanceof HumanoidModel<?>,
                        VanillaHumanoidCompat.isRegisteredByEfpi(id),
                        VanillaHumanoidCompat.isCompatibilityEnabled(id),
                        neutralize,
                        patch != null && patch.getArmature()
                                instanceof yesman.epicfight.model.armature.HumanoidArmature,
                        executionSupported,
                        posture != null,
                        patch != null && patch.overrideRender()
                );
            } catch (Throwable ex) {
                LOGGER.warn("EFPI souls-diagnostic/client failed for {}", id, ex);
            }
        }
    }

    private SoulsWeaponryClientDiagnostics() {}
}
