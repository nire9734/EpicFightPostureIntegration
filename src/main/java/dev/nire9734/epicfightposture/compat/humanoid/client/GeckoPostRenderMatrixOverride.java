package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;

/**
 * One-shot render-thread bridge for Gecko renderers that draw attachments after
 * {@code GeoEntityRenderer.render()} returns.
 *
 * <p>Normal Gecko render layers are handled directly by {@link GeckoAttachmentBridge}.
 * A few renderers (notably Iron's Spells' HumanoidRenderer cape path) later call
 * {@code RenderUtils.prepMatrixForBone} themselves. The patched EF renderer runs
 * while Gecko's pre-render event is being cancelled, immediately before that
 * post-render code resumes, so a one-shot ThreadLocal lets the exact matrix be
 * handed to that call without reconstructing it as Gecko Euler values.</p>
 */
public final class GeckoPostRenderMatrixOverride {
    private static final ThreadLocal<Entry> NEXT = new ThreadLocal<>();

    public static void clear() {
        NEXT.remove();
    }

    public static void arm(CoreGeoBone bone, Matrix4f matrix) {
        if (bone == null || matrix == null) {
            NEXT.remove();
            return;
        }

        NEXT.set(new Entry(bone, new Matrix4f(matrix)));
    }

    /**
     * Applies and consumes the pending matrix only for the exact GeoBone that
     * armed it. Returning false means the caller should execute GeckoLib's
     * normal {@code prepMatrixForBone} implementation.
     */
    public static boolean applyIfPresent(PoseStack poseStack, CoreGeoBone bone) {
        Entry entry = NEXT.get();
        if (entry == null || entry.bone() != bone) {
            return false;
        }

        NEXT.remove();
        poseStack.mulPoseMatrix(new Matrix4f(entry.matrix()));
        return true;
    }

    private record Entry(CoreGeoBone bone, Matrix4f matrix) {
    }

    private GeckoPostRenderMatrixOverride() {
    }
}
