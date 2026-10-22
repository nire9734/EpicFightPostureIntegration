package dev.nire9734.epicfightposture.neoforge.client;

import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Prevent stale UUID/entity-id entries when switching worlds or servers. */
@EventBusSubscriber(modid = "epicfight_posture_integration", value = Dist.CLIENT)
public final class NeoForgePostureClientEvents {
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        NeoForgeAutoCompatibility.recoverClientRegistrationsOnWorldJoin();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPostureStore.clear();
        NeoForgeServerSettings.clearRemoteSnapshot();
    }

    private NeoForgePostureClientEvents() {}
}
