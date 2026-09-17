package dev.nire9734.epicfightposture;

import dev.nire9734.epicfightposture.network.BridgeNetwork;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;

@Mod(EpicFightPostureIntegration.MODID)
public final class EpicFightPostureIntegration {
    public static final String MODID = "epicfight_posture_integration";

    public EpicFightPostureIntegration() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, PostureServerConfig.SPEC,
                "epicfight_posture_integration-server.toml");
        PostureServerConfig.refreshBlacklist();
        BridgeNetwork.init();
    }
}
