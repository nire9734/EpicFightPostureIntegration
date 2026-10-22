package dev.nire9734.epicfightposture.compat.humanoid.client;

/**
 * Cheap render-thread hints for optional EFPI hooks that are globally mixed into
 * Minecraft/Gecko/Lionfish render classes.
 *
 * <p>The expensive compatibility contexts remain authoritative. These counters
 * only let normal rendering return before touching ThreadLocal state or doing any
 * model preparation when no EFPI special-state replay is active.</p>
 */
public final class SpecialRenderHooks {
    private static int layerReplayDepth;
    private static int geckoArmorDepth;

    static void beginLayerReplay() {
        layerReplayDepth++;
    }

    static void endLayerReplay() {
        if (layerReplayDepth > 0) {
            layerReplayDepth--;
        }
    }

    public static boolean isLayerReplayActive() {
        return layerReplayDepth != 0;
    }

    static void beginGeckoArmorReplay() {
        geckoArmorDepth++;
    }

    static void endGeckoArmorReplay() {
        if (geckoArmorDepth > 0) {
            geckoArmorDepth--;
        }
    }

    public static boolean isGeckoArmorReplayActive() {
        return geckoArmorDepth != 0;
    }

    private SpecialRenderHooks() {
    }
}
