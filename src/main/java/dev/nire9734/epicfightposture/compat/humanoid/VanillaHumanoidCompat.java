package dev.nire9734.epicfightposture.compat.humanoid;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;

import java.util.List;

/**
 * Experimental compatibility registry for third-party mobs rendered with the
 * vanilla HumanoidModel family.
 *
 * <p>The renderer type is client-only information, while Epic Fight patch
 * registration must also happen on the logical server. For this first test build
 * the entity IDs are an explicit allow-list. Once the adapter is validated this
 * list can be moved to a data/config driven profile system.</p>
 */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class VanillaHumanoidCompat {
    /** Known 1.20.1 test targets whose render models extend HumanoidModel. */
    public static final List<ResourceLocation> TEST_TARGETS = List.of(
            new ResourceLocation("majruszsdifficulty", "cursed_armor"),
            new ResourceLocation("botania", "doppleganger")
    );

    public static void bootstrap() {
        // Explicit class-load point from the main mod class.
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        for (ResourceLocation entityId : TEST_TARGETS) {
            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
            if (entityType == null || event.getTypeEntry().containsKey(entityType)) {
                continue;
            }

            // BIPED is used only as the internal animation source. No patched
            // renderer is registered, so the third-party HumanoidModel renderer
            // remains active in normal gameplay.
            Armatures.registerEntityTypeArmature(entityType, Armatures.BIPED);
            event.getTypeEntry().put(entityType, ignored -> LightweightHumanoidPatch::new);
        }
    }

    private VanillaHumanoidCompat() {
    }
}
