package dev.nire9734.epicfightposture;

import dev.nire9734.epicfightposture.client.ClientConfigRegistration;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import dev.nire9734.epicfightposture.network.BridgeNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(EpicFightPostureIntegration.MODID)
public final class EpicFightPostureIntegration {
    public static final String MODID = "epicfight_posture_integration";

    public EpicFightPostureIntegration() {
        ModLoadingContext context = ModLoadingContext.get();

        context.registerConfig(
                ModConfig.Type.SERVER,
                PostureServerConfig.SPEC,
                "epicfight_posture_integration-server.toml"
        );

        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.registerConfig(
                    ModConfig.Type.CLIENT,
                    PostureClientConfig.SPEC,
                    "epicfight_posture_integration-client.toml"
            );
            ClientConfigRegistration.register(context);
        }

        PostureServerConfig.refreshBlacklist();
        VanillaHumanoidCompat.bootstrap();
        BridgeNetwork.init();
    }
}
