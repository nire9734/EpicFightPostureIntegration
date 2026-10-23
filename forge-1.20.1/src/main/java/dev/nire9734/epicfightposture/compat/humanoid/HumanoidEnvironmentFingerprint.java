package dev.nire9734.epicfightposture.compat.humanoid;

import net.minecraftforge.fml.ModList;

import java.util.List;

/** Stable hash of the currently loaded mod IDs and versions. */
public final class HumanoidEnvironmentFingerprint {
    public static String current() {
        List<String> entries = ModList.get().getMods().stream()
                .map(info -> info.getModId() + "@" + info.getVersion())
                .sorted()
                .toList();
        return ModSetFingerprint.fromEntries(entries);
    }

    private HumanoidEnvironmentFingerprint() {
    }
}
