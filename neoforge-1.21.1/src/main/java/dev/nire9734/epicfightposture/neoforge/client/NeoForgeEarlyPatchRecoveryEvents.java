package dev.nire9734.epicfightposture.neoforge.client;

import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Recover missing EFPI providers before normal entity-join patch consumers. */
@EventBusSubscriber(modid = "epicfight_posture_integration", value = Dist.CLIENT)
public final class NeoForgeEarlyPatchRecoveryEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() != null) {
            NeoForgeAutoCompatibility.recoverClientProviderBeforeEntityJoin(event.getEntity().getType());
        }
    }

    private NeoForgeEarlyPatchRecoveryEvents() {
    }
}
