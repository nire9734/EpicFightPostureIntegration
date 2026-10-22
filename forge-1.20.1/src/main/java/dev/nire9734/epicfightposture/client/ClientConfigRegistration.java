package dev.nire9734.epicfightposture.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/** Client-only registration kept outside the common mod class to avoid dedicated-server class loading. */
public final class ClientConfigRegistration {
    public static void register(ModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new PostureConfigScreen(parent)
                )
        );
    }

    private ClientConfigRegistration() {
    }
}
