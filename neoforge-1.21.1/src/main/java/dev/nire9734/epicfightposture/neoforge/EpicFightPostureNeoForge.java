package dev.nire9734.epicfightposture.neoforge;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureNetworking;
import net.neoforged.fml.config.ModConfig;
import dev.nire9734.epicfightposture.NeoForgePostureServerEvents;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeRendererDiscoveryServer;

/**
 * NeoForge 1.21.1 incomplete port with server-side posture hooks.
 * Rendering, network synchronization and general compatibility are pending.
 */
@Mod(EpicFightPostureNeoForge.MODID)
public final class EpicFightPostureNeoForge {
    public static final String MODID = "epicfight_posture_integration";
    private static final Logger LOGGER = LogUtils.getLogger();

    public EpicFightPostureNeoForge(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, NeoForgePostureConfig.SPEC);
        modBus.addListener(NeoForgePostureNetworking::registerPayloads);
        NeoForgeRendererDiscoveryServer.bootstrap();
        NeoForgeAutoCompatibility.register();
        NeoForgePostureServerEvents.register();
        LOGGER.warn("EFPI NeoForge development port: HumanoidModel scanning and trusted dedicated-server discovery are experimental; validate all gameplay before release");
    }
}
