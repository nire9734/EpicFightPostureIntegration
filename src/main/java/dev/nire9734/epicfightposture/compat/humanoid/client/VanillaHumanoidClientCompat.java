package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.HumanoidCompatConfig;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Client-side HumanoidModel discovery and special-state EF renderer registration. */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class VanillaHumanoidClientCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onAddPatchedRenderers(PatchedRenderersEvent.Add event) {
        Set<EntityType<?>> alreadyPatched = new HashSet<>(EntityPatchProvider.getPatchedEntities());
        Set<ResourceLocation> detected = new HashSet<>();

        for (Map.Entry<EntityType<?>, EntityRenderer<?>> entry
                : event.getContext().getEntityRenderDispatcher().renderers.entrySet()) {
            EntityType<?> entityType = entry.getKey();
            EntityRenderer<?> renderer = entry.getValue();

            if (entityType == null || entityType == EntityType.PLAYER || !(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)) {
                continue;
            }
            if (!(livingRenderer.getModel() instanceof HumanoidModel<?>)) {
                continue;
            }

            ResourceLocation entityId = EntityType.getKey(entityType);
            if (entityId == null || HumanoidCompatConfig.isBlacklisted(entityId)) {
                continue;
            }

            boolean oursAlready = VanillaHumanoidCompat.isKnownCompatibilityTarget(entityId);
            if (alreadyPatched.contains(entityType) && !oursAlready) {
                // Never replace an Epic Fight patch supplied by Epic Fight itself,
                // another compatibility mod, or an earlier registry subscriber.
                continue;
            }

            if (HumanoidCompatConfig.autoDetect()) {
                detected.add(entityId);
            } else if (!HumanoidCompatConfig.forceInclude().contains(entityId)) {
                continue;
            }

            if (!oursAlready) {
                VanillaHumanoidCompat.registerDiscoveredAtRuntime(entityType);
            }

            // SpecialStateHumanoidRenderer explicitly patches the standard humanoid
            // layers (armor, held items, head items and elytra). Do NOT call
            // initLayerLast(): unknown mod layers may redraw the entire source model
            // (Guard Villagers does this for its variant layer), causing double bodies.
            event.addPatchedEntityRenderer(
                    entityType,
                    type -> new SpecialStateHumanoidRenderer(event.getContext(), type)
            );
        }

        if (HumanoidCompatConfig.autoDetect()) {
            VanillaHumanoidCompat.replaceDetectedCache(detected);
            LOGGER.info("EFPI auto-detected {} third-party/extra HumanoidModel entity type(s)", detected.size());
        }
    }

    private VanillaHumanoidClientCompat() {
    }
}
