package dev.nire9734.epicfightposture.neoforge.compat;

/**
 * Independent client rendering contract. Rendering is never activated unless
 * a verified renderer, active compatibility registration and synchronized
 * server guard-break state are all present.
 */
public final class NeoForgeSpecialRenderPolicy {
    public static boolean shouldOverride(boolean clientSide, boolean compatibilityEnabled,
                                         boolean meshAndRendererVerified, boolean brokenOrExecuting) {
        return clientSide && compatibilityEnabled && meshAndRendererVerified && brokenOrExecuting;
    }

    private NeoForgeSpecialRenderPolicy() {}
}
