package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Version-neutral matrix composition for 1.20.1 and 1.21.1 render-layer replay. */
public final class PoseStackMatrixTransform {
    private static final float MATRIX_EPSILON = 1.0E-8F;

    public static void multiplyPositionAndNormal(PoseStack stack, Matrix4f transform) {
        // Both Minecraft versions' PoseStack matrix overloads multiply the position matrix.
        // Updating the JOML pose directly avoids the renamed loader/version API.
        stack.last().pose().mul(new Matrix4f(transform));

        Matrix3f normalTransform = new Matrix3f(transform);
        float determinant = normalTransform.determinant();
        if (Float.isFinite(determinant) && Math.abs(determinant) > MATRIX_EPSILON) {
            normalTransform.invert().transpose();
            stack.last().normal().mul(normalTransform);
        }
    }

    private PoseStackMatrixTransform() {
    }
}
