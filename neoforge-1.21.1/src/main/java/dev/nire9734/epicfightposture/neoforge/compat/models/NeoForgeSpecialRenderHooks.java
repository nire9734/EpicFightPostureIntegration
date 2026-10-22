package dev.nire9734.epicfightposture.neoforge.compat.models;

/** Cheap guard for globally installed optional render Mixins. */
public final class NeoForgeSpecialRenderHooks {
    private static final ThreadLocal<Integer> LAYER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> GECKO_ARMOR_DEPTH = ThreadLocal.withInitial(() -> 0);

    static Scope beginLayerReplay() {
        LAYER_DEPTH.set(LAYER_DEPTH.get() + 1);
        return new Scope();
    }

    public static boolean isLayerReplayActive() {
        return LAYER_DEPTH.get() > 0;
    }

    static GeckoArmorScope beginGeckoArmorReplay() {
        GECKO_ARMOR_DEPTH.set(GECKO_ARMOR_DEPTH.get() + 1);
        return new GeckoArmorScope();
    }

    public static boolean isGeckoArmorReplayActive() {
        return GECKO_ARMOR_DEPTH.get() > 0;
    }

    static final class GeckoArmorScope implements AutoCloseable {
        private boolean closed;

        @Override public void close() {
            if (closed) return;
            closed = true;
            int depth = GECKO_ARMOR_DEPTH.get() - 1;
            if (depth <= 0) GECKO_ARMOR_DEPTH.remove();
            else GECKO_ARMOR_DEPTH.set(depth);
        }
    }

    static final class Scope implements AutoCloseable {
        private boolean closed;
        @Override public void close() {
            if (closed) return;
            closed = true;
            int depth = LAYER_DEPTH.get() - 1;
            if (depth <= 0) LAYER_DEPTH.remove();
            else LAYER_DEPTH.set(depth);
        }
    }

    private NeoForgeSpecialRenderHooks() {}
}
