package dev.nire9734.epicfightposture.neoforge.compat.models;

/**
 * Minecraft 1.21.1 Model#renderToBuffer passes one packed ARGB color, unlike
 * the four floats used by the Forge 1.20.1 Lionfish renderer.
 */
public record NeoForgeLayerColor(float red, float green, float blue, float alpha) {
    public static NeoForgeLayerColor fromArgb(int packed) {
        return new NeoForgeLayerColor(
                ((packed >>> 16) & 255) / 255.0F,
                ((packed >>> 8) & 255) / 255.0F,
                (packed & 255) / 255.0F,
                ((packed >>> 24) & 255) / 255.0F);
    }
}
