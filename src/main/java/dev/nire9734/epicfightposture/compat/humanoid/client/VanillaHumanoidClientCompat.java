package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;

/** Client-side renderer registration for the explicit HumanoidModel allow-list. */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class VanillaHumanoidClientCompat {
    @SubscribeEvent
    public static void onAddPatchedRenderers(PatchedRenderersEvent.Add event) {
        for (ResourceLocation entityId : VanillaHumanoidCompat.TEST_TARGETS) {
            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (entityType == null) {
                continue;
            }

            // SpecialStateHumanoidRenderer explicitly patches the standard humanoid
            // layers (armor, held items, head items and elytra). Do NOT call
            // initLayerLast() here: Epic Fight's default fallback wraps every unknown
            // mod layer in RenderOriginalModelLayer. Some mods (notably Guard
            // Villagers' GuardVariantLayer) render the entire parent HumanoidModel
            // again, which produces a second unskinned body/extra-looking arms while
            // the EF skinned mesh is active. Unknown source-mod layers are therefore
            // intentionally suppressed only during BROKEN/EXECUTING rendering.
            event.addPatchedEntityRenderer(
                    entityType,
                    type -> new SpecialStateHumanoidRenderer(event.getContext(), type)
            );
        }
    }

    private VanillaHumanoidClientCompat() {
    }
}
