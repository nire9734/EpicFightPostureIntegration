package dev.nire9734.epicfightposture.neoforge.network;

import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeRendererScanRequest;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeRendererScanReport;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeRendererDiscoveryServer;
import dev.nire9734.epicfightposture.neoforge.client.NeoForgeRendererDiscoveryClient;

/**
 * NeoForge 1.21.1 play-phase protocol. ClientPostureStore is plain Java so
 * its packet callback is safe to link on a physical dedicated server.
 */
public final class NeoForgePostureNetworking {
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(
                PostureStatePayload.TYPE, PostureStatePayload.STREAM_CODEC,
                NeoForgePostureNetworking::receiveOnClient);
        registrar.playToClient(
                NeoForgeServerSettingsPayload.TYPE, NeoForgeServerSettingsPayload.STREAM_CODEC,
                NeoForgePostureNetworking::receiveSettingsOnClient);
        registrar.playToClient(
                NeoForgeRendererScanRequest.TYPE, NeoForgeRendererScanRequest.STREAM_CODEC,
                (packet, context) -> NeoForgeRendererDiscoveryClient.replyToServer(packet.nonce()));
        registrar.playToServer(
                NeoForgeRendererScanReport.TYPE, NeoForgeRendererScanReport.STREAM_CODEC,
                (packet, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        NeoForgeRendererDiscoveryServer.acceptReport(player, packet);
                    }
                });
    }

    private static void receiveSettingsOnClient(NeoForgeServerSettingsPayload payload, IPayloadContext context) {
        NeoForgeServerSettings.applyRemoteSnapshot(payload.snapshot());
        // Providers can be rebuilt during a previous integrated-server world unload.
        NeoForgeAutoCompatibility.recoverClientRegistrationsOnWorldJoin();
    }

    private static void receiveOnClient(PostureStatePayload payload, IPayloadContext context) {
        // NeoForge's default payload handler thread is the logical main thread.
        if (payload.present()) {
            if (payload.uuid() != null
                    && Float.isFinite(payload.current()) && Float.isFinite(payload.max())
                    && payload.max() > 0.0F) {
                ClientPostureStore.update(payload.entityId(), payload.uuid(),
                        payload.current(), payload.max(), payload.broken());
            }
        } else {
            ClientPostureStore.remove(payload.entityId(), payload.uuid());
        }
    }

    private NeoForgePostureNetworking() {}
}
