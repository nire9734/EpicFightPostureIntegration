package dev.nire9734.epicfightposture.neoforge.client;

import java.util.List;

/** Portable implementation of Forge's exact-one-backend admission rule. */
public final class NeoForgeBackendSelection {
    public static <T> T unique(List<? extends T> prepared) {
        return prepared != null && prepared.size() == 1 ? prepared.get(0) : null;
    }

    private NeoForgeBackendSelection() {}
}
