package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.client.ClientConfigRegistration;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import dev.nire9734.epicfightposture.network.BridgeNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Mod(EpicFightPostureIntegration.MODID)
public final class EpicFightPostureIntegration {
    public static final String MODID = "epicfight_posture_integration";
    private static final Logger LOGGER = LogUtils.getLogger();

    public EpicFightPostureIntegration() {
        ModLoadingContext context = ModLoadingContext.get();
        prepareConfigLocation();

        // EFPI server policy is installation/server-global rather than per-world.
        // The authoritative values are synchronized to clients after login.
        context.registerConfig(
                ModConfig.Type.COMMON,
                PostureServerConfig.SPEC,
                MODID + "/server.toml"
        );

        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.registerConfig(
                    ModConfig.Type.CLIENT,
                    PostureClientConfig.SPEC,
                    MODID + "/client.toml"
            );
            ClientConfigRegistration.register(context);
        }

        PostureServerConfig.refreshBlacklist();
        VanillaHumanoidCompat.bootstrap();
        BridgeNetwork.init();
    }

    private static void prepareConfigLocation() {
        Path configDirectory = FMLPaths.CONFIGDIR.get();
        Path modDirectory = configDirectory.resolve(MODID);
        Path legacyClientConfig = configDirectory.resolve(MODID + "-client.toml");
        Path legacyGlobalServerConfig = configDirectory.resolve(MODID + "-server.toml");
        Path legacyDiscoveryCache = configDirectory.resolve(MODID + "-humanoid-detected.json");
        Path newClientConfig = modDirectory.resolve("client.toml");
        Path newServerConfig = modDirectory.resolve("server.toml");
        Path obsoleteDiscoveryCache = modDirectory.resolve("humanoid-detected.json");

        try {
            Files.createDirectories(modDirectory);
            if (Files.isRegularFile(legacyClientConfig) && !Files.exists(newClientConfig)) {
                Files.move(legacyClientConfig, newClientConfig, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved EFPI client config to {}", newClientConfig);
            }
            if (Files.isRegularFile(legacyGlobalServerConfig) && !Files.exists(newServerConfig)) {
                Files.move(legacyGlobalServerConfig, newServerConfig, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved EFPI global server config to {}", newServerConfig);
            }

            // Client renderer discovery is now validated fresh each launch and kept
            // in memory only. Remove obsolete cache files from pre-release builds.
            Files.deleteIfExists(obsoleteDiscoveryCache);
            Files.deleteIfExists(legacyDiscoveryCache);
        } catch (IOException exception) {
            LOGGER.warn("Failed to prepare EFPI config directory {}", modDirectory, exception);
        }
    }
}
