package dev.nire9734.epicfightposture.neoforge.client;

import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.client.ServerVersionNotice;
import dev.nire9734.epicfightposture.neoforge.network.NeoForgeServerVersionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

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
        ServerVersionNotice.clear();
    }

    /** Shows the one-line version notice once the chat is available after login. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ServerVersionNotice.hasPending()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        String local = NeoForgeServerVersionPayload.localModVersion();
        String server = ServerVersionNotice.takeMismatch(local);
        if (server != null) {
            minecraft.gui.getChat().addMessage(Component.translatable(
                    ServerVersionNotice.TRANSLATION_KEY, server, local).withStyle(ChatFormatting.YELLOW));
        }
    }

    private NeoForgePostureClientEvents() {}
}
