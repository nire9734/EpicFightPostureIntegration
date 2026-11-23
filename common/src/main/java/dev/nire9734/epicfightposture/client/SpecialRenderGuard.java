package dev.nire9734.epicfightposture.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.SpecialRenderFailures;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

/**
 * Crash barrier around every special-state (guard break / execution) renderer.
 *
 * Epic Fight calls patched renderers without catching exceptions, so any
 * failure in a model conversion, a third-party render layer or a stale mesh
 * would crash the client. This guard instead:
 * <ul>
 *   <li>catches the failure, logs it once per entity type and disables the
 *       special renderer for that type until renderers are rebuilt;</li>
 *   <li>restores the PoseStack to the depth it had on entry, so a layer that
 *       threw between pushPose and popPose cannot trigger Minecraft's
 *       end-of-frame "Pose stack not empty" crash.</li>
 * </ul>
 * Fatal JVM errors are never swallowed.
 */
public final class SpecialRenderGuard {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Upper bound on pops while restoring; a real render never nests this deep. */
    private static final int MAX_RESTORE_POPS = 64;

    public static void render(LivingEntity entity, PoseStack poseStack, Runnable body) {
        PoseStack.Pose entry = poseStack.last();
        try {
            body.run();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            Object type = entity == null ? null : entity.getType();
            if (SpecialRenderFailures.disable(type)) {
                LOGGER.error("Epic Fight Posture Integration disabled the guard break renderer for {}"
                        + " after an error; it renders normally until resources are reloaded", type, failure);
            }
        } finally {
            if (restore(poseStack, entry)) {
                LOGGER.debug("Restored an unbalanced PoseStack after special rendering of {}",
                        entity == null ? null : entity.getType());
            }
        }
    }

    /**
     * Pops back to {@code entry}. Returns true when the stack was unbalanced.
     * Bounded, and never pops the root entry.
     */
    static boolean restore(PoseStack poseStack, PoseStack.Pose entry) {
        if (poseStack.last() == entry) return false;
        for (int i = 0; i < MAX_RESTORE_POPS && poseStack.last() != entry && !poseStack.clear(); i++) {
            poseStack.popPose();
        }
        return true;
    }

    private SpecialRenderGuard() {
    }
}
