package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.platform.GlDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic compatibility workaround for the extremely noisy OpenGL debug
 * message emitted by Epic Fight's compute-shader path on the affected AMD
 * OpenGL setup.
 *
 * <p>This does not change compute-shader rendering or suppress any other OpenGL
 * debug message. It only drops message id 1005 before Minecraft converts the
 * native message to a String, updates GlDebug's message buffer, and sends the
 * entry through Log4j.</p>
 */
@Mixin(GlDebug.class)
public abstract class GlDebug1005SuppressionMixin {
    private static final int EFPI_NOISY_GL_MESSAGE_ID = 1005;

    @Inject(method = "printDebugLog", at = @At("HEAD"), cancellable = true)
    private static void efpi$suppressNoisyComputeDebugMessage(
            int source,
            int type,
            int id,
            int severity,
            int length,
            long messagePointer,
            long userParam,
            CallbackInfo ci) {
        if (id == EFPI_NOISY_GL_MESSAGE_ID) {
            ci.cancel();
        }
    }
}
