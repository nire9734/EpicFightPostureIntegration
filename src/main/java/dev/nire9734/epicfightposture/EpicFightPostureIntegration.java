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

@Mod(EpicFightPostureIntegration.MODID)
public final class EpicFightPostureIntegration {
    public static final String MODID = "epicfight_posture_integration";
    private static final Logger LOGGER = LogUtils.getLogger();

    public EpicFightPostureIntegration() {
        ModLoadingContext context = ModLoadingContext.get();
        prepareConfigDirectory();

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

        VanillaHumanoidCompat.bootstrap();
        BridgeNetwork.init();
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
