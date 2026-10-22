package dev.nire9734.epicfightposture.neoforge.compat.models;

/**
 * Match Forge 3.0.2 Lionfish admission: the shared semantic resolver and
 * mesh baker decide whether a model is supported. Rig quality is diagnostic
 * and must not become an additional NeoForge-only cutoff.
 */
public final class NeoForgeLionfishAdmission {
    public static boolean accepts(boolean supported, boolean hasSkeleton,
                                  boolean hasMesh, float quality) {
        return supported && hasSkeleton && hasMesh
                && Float.isFinite(quality) && quality >= 0.0F && quality <= 1.0F;
    }

    private NeoForgeLionfishAdmission() {}
}
