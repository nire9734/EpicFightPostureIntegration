package dev.nire9734.epicfightposture.neoforge.network;

import dev.nire9734.epicfightposture.neoforge.NeoForgePostureConfig;
import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Sends the server's policy at login and after a live config reload. */
public final class NeoForgeServerSettingsSync {
    private static boolean registered;

    public static synchronized void register(IEventBus modBus) {
        if (registered) return;
        registered = true;
        modBus.addListener(NeoForgeServerSettingsSync::onLoading);
        modBus.addListener(NeoForgeServerSettingsSync::onReloading);
        NeoForge.EVENT_BUS.addListener(NeoForgeServerSettingsSync::onLogin);
    }

    private static void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == NeoForgePostureConfig.SPEC) {
            NeoForgeServerSettings.invalidateLocal();
        }
    }

    private static void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != NeoForgePostureConfig.SPEC) return;
        NeoForgeServerSettings.invalidateLocal();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Config watcher events may run off the logical server thread.
            server.execute(() -> {
                NeoForgeServerSettingsPayload packet = current();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    PacketDistributor.sendToPlayer(player, packet);
                }
            });
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, current());
        }
    }

    private static NeoForgeServerSettingsPayload current() {
        return new NeoForgeServerSettingsPayload(NeoForgeServerSettings.localSnapshot());
    }

    private NeoForgeServerSettingsSync() {}
}
