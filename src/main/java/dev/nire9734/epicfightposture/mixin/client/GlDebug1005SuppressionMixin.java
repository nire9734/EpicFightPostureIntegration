package dev.nire9734.epicfightposture.mixin.client;

import com.mojang.blaze3d.platform.GlDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Filters one known high-volume OpenGL debug callback observed on affected
 * Epic Fight compute-shader + AMD OpenGL configurations.
 *
 * <p>The workaround is intentionally narrow: it suppresses only the exact
 * API / ERROR / id 1005 / HIGH signature before Minecraft converts the native
 * message to a String, updates GlDebug's message buffer, and sends the entry
 * through Log4j. Compute-shader rendering itself is left unchanged and every
 * other OpenGL debug message remains visible.</p>
 */
@Mixin(GlDebug.class)
public abstract class GlDebug1005SuppressionMixin {
    private static final int EFPI_GL_SOURCE_API = 33350;
    private static final int EFPI_GL_TYPE_ERROR = 33356;
    private static final int EFPI_GL_MESSAGE_ID = 1005;
    private static final int EFPI_GL_SEVERITY_HIGH = 37190;

    @Inject(method = "printDebugLog", at = @At("HEAD"), cancellable = true)
    private static void efpi$suppressKnownComputeDebugNoise(
            int source,
            int type,
            int id,
            int severity,
            int length,
            long messagePointer,
            long userParam,
            CallbackInfo ci) {
        if (source == EFPI_GL_SOURCE_API
                && type == EFPI_GL_TYPE_ERROR
                && id == EFPI_GL_MESSAGE_ID
                && severity == EFPI_GL_SEVERITY_HIGH) {
            ci.cancel();
        }
    }
}
