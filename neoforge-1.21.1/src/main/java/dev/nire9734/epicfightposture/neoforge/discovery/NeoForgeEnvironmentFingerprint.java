package dev.nire9734.epicfightposture.neoforge.discovery;

import net.neoforged.fml.ModList;
import dev.nire9734.epicfightposture.compat.humanoid.ModSetFingerprint;
import java.util.List;

/** Stable hash of the currently loaded mod IDs and versions. */
public final class NeoForgeEnvironmentFingerprint {
    public static String current() {
        List<String> entries = ModList.get().getMods().stream()
                .map(info -> info.getModId() + "@" + info.getVersion())
                .sorted()
                .toList();
        return ModSetFingerprint.fromEntries(entries);
    }

    private NeoForgeEnvironmentFingerprint() {
    }
}
