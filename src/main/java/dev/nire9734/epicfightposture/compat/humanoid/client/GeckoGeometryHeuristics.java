package dev.nire9734.epicfightposture.compat.humanoid.client;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.Locale;

/**
 * Gecko-specific geometry classification that stays below the shared humanoid
 * semantic layer.
 *
 * <p>Some Gecko entity models carry inflated cubes only so ItemArmorGeoLayer can
 * read a reference size/pivot when fitting worn armor. These helper cubes are
 * commonly authored with an empty UV map. They are not presentation geometry and
 * must not be baked into EFPI's always-visible body mesh or body bounds.</p>
 *
 * <p>Do not classify by an "armor" name alone: many mobs have permanently modeled
 * armor with real texture coordinates. A cube is treated as a fitting/reference
 * helper only when both the bone name is reference-like and all of its UVs are
 * degenerate.</p>
 */
final class GeckoGeometryHeuristics {
    private static final float UV_EPSILON = 1.0E-6F;

    static boolean isLayoutReferenceCube(GeoBone bone, GeoCube cube) {
        return bone != null
                && cube != null
                && referenceLikeName(bone.getName())
                && !hasMeaningfulTextureCoordinates(cube);
    }

    static boolean hasMeaningfulTextureCoordinates(GeoCube cube) {
        if (cube == null || cube.quads() == null || cube.quads().length == 0) {
            return false;
        }

        for (GeoQuad quad : cube.quads()) {
            if (quad == null || quad.vertices() == null || quad.vertices().length == 0) {
                continue;
            }

            float minU = Float.POSITIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;
            boolean any = false;

            for (GeoVertex vertex : quad.vertices()) {
                if (vertex == null || !Float.isFinite(vertex.texU()) || !Float.isFinite(vertex.texV())) {
                    continue;
                }
                any = true;
                minU = Math.min(minU, vertex.texU());
                minV = Math.min(minV, vertex.texV());
                maxU = Math.max(maxU, vertex.texU());
                maxV = Math.max(maxV, vertex.texV());
            }

            if (any && (maxU - minU > UV_EPSILON || maxV - minV > UV_EPSILON)) {
                return true;
            }
        }
        return false;
    }

    private static boolean referenceLikeName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return false;
        }
        String name = rawName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return name.contains("armor")
                || name.contains("armour")
                || name.contains("equipment")
                || name.contains("equip")
                || name.contains("reference")
                || name.contains("helper")
                || name.contains("slot")
                || name.contains("fitting");
    }

    private GeckoGeometryHeuristics() {
    }
}
