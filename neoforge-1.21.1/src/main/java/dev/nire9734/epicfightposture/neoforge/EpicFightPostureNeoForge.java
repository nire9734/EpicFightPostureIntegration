package dev.nire9734.epicfightposture.neoforge;

import org.slf4j.Logger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureNetworking;
import dev.nire9734.epicfightposture.neoforge.network.NeoForgeServerSettingsSync;
import net.neoforged.fml.config.ModConfig;
import dev.nire9734.epicfightposture.NeoForgePostureServerEvents;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeRendererDiscoveryServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * NeoForge 1.21.1 entry point for the shared EFPI runtime.
 */
@Mod(EpicFightPostureNeoForge.MODID)
public final class EpicFightPostureNeoForge {
    public static final String MODID = "epicfight_posture_integration";
    private static final Logger LOGGER = LogUtils.getLogger();

    public EpicFightPostureNeoForge(IEventBus modBus, ModContainer modContainer) {
        prepareConfigDirectory();
        modContainer.registerConfig(ModConfig.Type.COMMON, NeoForgePostureConfig.SPEC,
                MODID + "/server.toml");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT, NeoForgePostureClientConfig.SPEC,
                    MODID + "/client.toml");
            dev.nire9734.epicfightposture.neoforge.client.NeoForgeClientConfigRegistration.register(modContainer);
        }
        modBus.addListener(NeoForgePostureNetworking::registerPayloads);
        NeoForgeServerSettingsSync.register(modBus);
        NeoForgeRendererDiscoveryServer.bootstrap();
        NeoForgeAutoCompatibility.register();
        NeoForgePostureServerEvents.register();
    }

    private static void prepareConfigDirectory() {
        Path modDirectory = FMLPaths.CONFIGDIR.get().resolve(MODID);
        try {
            Files.createDirectories(modDirectory);
        } catch (IOException exception) {
            LOGGER.warn("Failed to prepare EFPI config directory {}", modDirectory, exception);
        }
    }
}
