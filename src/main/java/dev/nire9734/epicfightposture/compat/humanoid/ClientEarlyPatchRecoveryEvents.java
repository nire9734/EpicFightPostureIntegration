package dev.nire9734.epicfightposture.compat.humanoid;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Epic Fight's normal-priority attachment handler chooses its patch at entity
 * construction time. Recover an EFPI-owned provider at HIGHEST priority, before
 * that handler sees an entity loaded from a saved integrated-server world.
 */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class ClientEarlyPatchRecoveryEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeEpicFightAttach(AttachCapabilitiesEvent<Entity> event) {
        Entity entity = event.getObject();
        if (entity != null) {
            VanillaHumanoidCompat.recoverClientProviderBeforeEntityAttachment(entity.getType());
        }
    }

    private ClientEarlyPatchRecoveryEvents() {
    }
}
