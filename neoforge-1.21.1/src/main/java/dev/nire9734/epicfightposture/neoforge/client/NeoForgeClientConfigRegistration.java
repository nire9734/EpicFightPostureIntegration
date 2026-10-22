package dev.nire9734.epicfightposture.neoforge.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only registration avoids linking Minecraft screen types on a dedicated server. */
public final class NeoForgeClientConfigRegistration {
    public static void register(ModContainer container) {
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                () -> (mod, parent) -> new NeoForgePostureConfigScreen(parent));
    }

    private NeoForgeClientConfigRegistration() {}
}
