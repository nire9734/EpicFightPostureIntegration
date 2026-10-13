package dev.nire9734.epicfightposture.network;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sends the authoritative global EFPI server policy to every joining client. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerSettingsSyncEvents {
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BridgeNetwork.sendPlayer(player, ServerSettingsSyncPacket.current());
        }
    }

    private ServerSettingsSyncEvents() {
    }
}
