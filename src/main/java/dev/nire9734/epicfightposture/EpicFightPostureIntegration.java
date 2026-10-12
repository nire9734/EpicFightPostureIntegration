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

        context.registerConfig(
                ModConfig.Type.SERVER,
                PostureServerConfig.SPEC,
                "epicfight_posture_integration-server.toml"
        );

        if (FMLEnvironment.dist == Dist.CLIENT) {
            prepareClientConfigLocation();
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

    private static void prepareClientConfigLocation() {
        Path configDirectory = FMLPaths.CONFIGDIR.get();
        Path modDirectory = configDirectory.resolve(MODID);
        Path legacyClientConfig = configDirectory.resolve(MODID + "-client.toml");
        Path newClientConfig = modDirectory.resolve("client.toml");

        try {
            Files.createDirectories(modDirectory);
            if (Files.isRegularFile(legacyClientConfig) && !Files.exists(newClientConfig)) {
                Files.move(legacyClientConfig, newClientConfig, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved EFPI client config to {}", newClientConfig);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to prepare EFPI client config directory {}", modDirectory, exception);
        }
    }
}
