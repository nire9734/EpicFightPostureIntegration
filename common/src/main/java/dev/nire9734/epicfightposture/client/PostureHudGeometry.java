package dev.nire9734.epicfightposture.client;

/** Loader-independent CE-textured health/posture bar geometry and UV coordinates. */
public final class PostureHudGeometry {
    public record Quad(float left, float top, float right, float bottom,
                       int u1, int v1, int u2, int v2) { }

    public record Layout(Quad healthBackground, Quad healthFill,
                         Quad postureBackground, Quad postureFill) { }

    public static Layout layout(float healthPercent, float posturePercent, boolean broken) {
        float hp = clamp(healthPercent);
        float pp = clamp(posturePercent);
        float scale = 1.0F;
        int textureLeft = 3, textureRight = 83, textureWidth = textureRight - textureLeft;
        int healthLeft = 6, healthRight = 80, healthWidth = healthRight - healthLeft;

        float healthHeight = scale * 20.0F / textureWidth;
        float healthX = -scale / 2.0F;
        float fillWidth = scale * (float) healthWidth / textureWidth * hp;
        float fillX = healthX + scale * (float) (healthLeft - textureLeft) / textureWidth;

        Quad background = new Quad(healthX, -healthHeight / 2.0F,
                healthX + scale, healthHeight / 2.0F, textureLeft, 0, textureRight, 20);
        Quad health = new Quad(fillX, -healthHeight / 2.0F,
                fillX + fillWidth, healthHeight / 2.0F,
                healthLeft, 21, (int) (healthLeft + healthWidth * hp), 41);

        int postureBgLeft = 6, postureBgRight = 80, postureBgWidth = postureBgRight - postureBgLeft;
        int postureFillLeft = 9, postureFillRight = 77;
        int postureFillWidth = postureFillRight - postureFillLeft;
        float postureWidth = scale * (float) postureBgWidth / textureWidth;
        float postureHeight = postureWidth * 20.0F / postureBgWidth;
        float postureX = -postureWidth / 2.0F;
        float postureFillX = postureX + postureWidth * (float) (postureFillLeft - postureBgLeft)
                / postureBgWidth;
        float postureFillScaledWidth = postureWidth * (float) postureFillWidth / postureBgWidth
                * (broken ? 1.0F : pp);

        Quad postureBackground = new Quad(postureX, -postureHeight / 2.0F,
                postureX + postureWidth, postureHeight / 2.0F,
                postureBgLeft, 42, postureBgRight, 62);
        Quad postureFill = new Quad(postureFillX, -postureHeight / 2.0F,
                postureFillX + postureFillScaledWidth, postureHeight / 2.0F,
                postureFillLeft, broken ? 84 : 63,
                broken ? postureFillRight : (int) (postureFillLeft + postureFillWidth * pp),
                broken ? 104 : 83);
        return new Layout(background, health, postureBackground, postureFill);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private PostureHudGeometry() { }
}
