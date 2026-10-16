package dev.nire9734.epicfightposture.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Low-overhead per-frame diagnostics used to isolate rare render stalls.
 *
 * <p>Counters are reset at Minecraft.runTick HEAD and only emitted when the full
 * frame exceeds the threshold. Normal frames do not allocate log messages.</p>
 */
public final class FrameSpikeDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SPIKE_THRESHOLD_NS = 40_000_000L;
    private static final long LOG_THROTTLE_NS = 200_000_000L;

    private static boolean frameActive;
    private static long frameStartNs;
    private static long lastLogNs;
    private static int suppressedSpikes;

    private static int specialRenders;
    private static int runtimeCpuDraws;
    private static int sourceModelPrepares;
    private static int layerReplays;
    private static int attachmentRenders;
    private static int lightweightPatchTicks;
    private static int overrideChecks;
    private static int overrideTrue;
    private static int modelPartHooks;
    private static int modelPartOverrides;
    private static int lionfishPartHooks;
    private static int lionfishPartOverrides;
    private static int bufferHooks;

    private static long specialRenderNs;
    private static long sourceModelPrepareNs;
    private static long layerReplayNs;
    private static long attachmentRenderNs;
    private static long lightweightPatchTickNs;

    public static void beginFrame() {
        frameStartNs = System.nanoTime();
        frameActive = true;

        specialRenders = 0;
        runtimeCpuDraws = 0;
        sourceModelPrepares = 0;
        layerReplays = 0;
        attachmentRenders = 0;
        lightweightPatchTicks = 0;
        overrideChecks = 0;
        overrideTrue = 0;
        modelPartHooks = 0;
        modelPartOverrides = 0;
        lionfishPartHooks = 0;
        lionfishPartOverrides = 0;
        bufferHooks = 0;

        specialRenderNs = 0L;
        sourceModelPrepareNs = 0L;
        layerReplayNs = 0L;
        attachmentRenderNs = 0L;
        lightweightPatchTickNs = 0L;
    }

    public static void endFrame() {
        if (!frameActive) {
            return;
        }

        long now = System.nanoTime();
        long frameNs = now - frameStartNs;
        frameActive = false;

        if (frameNs < SPIKE_THRESHOLD_NS) {
            return;
        }

        if (lastLogNs != 0L && now - lastLogNs < LOG_THROTTLE_NS) {
            suppressedSpikes++;
            return;
        }

        LOGGER.warn(
                "EFPI FRAME SPIKE: {} ms | special={} ({} ms) cpuDraws={} sourcePrep={} ({} ms) "
                        + "layers={} ({} ms) attachments={} ({} ms) patchTicks={} ({} ms) "
                        + "override={}/{} modelPartHooks={} overridden={} lionfishHooks={} overridden={} "
                        + "bufferHooks={} suppressedSinceLast={}",
                millis(frameNs),
                specialRenders,
                millis(specialRenderNs),
                runtimeCpuDraws,
                sourceModelPrepares,
                millis(sourceModelPrepareNs),
                layerReplays,
                millis(layerReplayNs),
                attachmentRenders,
                millis(attachmentRenderNs),
                lightweightPatchTicks,
                millis(lightweightPatchTickNs),
                overrideTrue,
                overrideChecks,
                modelPartHooks,
                modelPartOverrides,
                lionfishPartHooks,
                lionfishPartOverrides,
                bufferHooks,
                suppressedSpikes
        );

        suppressedSpikes = 0;
        lastLogNs = now;
    }

    public static long startTimedSection() {
        return frameActive ? System.nanoTime() : 0L;
    }

    public static void finishSpecialRender(long startedNs) {
        if (startedNs != 0L && frameActive) {
            specialRenders++;
            specialRenderNs += System.nanoTime() - startedNs;
        }
    }

    public static void recordRuntimeCpuDraw() {
        if (frameActive) {
            runtimeCpuDraws++;
        }
    }

    public static void finishSourceModelPrepare(long startedNs) {
        if (startedNs != 0L && frameActive) {
            sourceModelPrepares++;
            sourceModelPrepareNs += System.nanoTime() - startedNs;
        }
    }

    public static void finishLayerReplay(long startedNs) {
        if (startedNs != 0L && frameActive) {
            layerReplays++;
            layerReplayNs += System.nanoTime() - startedNs;
        }
    }

    public static void finishAttachmentRender(long startedNs) {
        if (startedNs != 0L && frameActive) {
            attachmentRenders++;
            attachmentRenderNs += System.nanoTime() - startedNs;
        }
    }

    public static void finishLightweightPatchTick(long startedNs) {
        if (startedNs != 0L && frameActive) {
            lightweightPatchTicks++;
            lightweightPatchTickNs += System.nanoTime() - startedNs;
        }
    }

    public static void recordOverrideCheck(boolean overriding) {
        if (frameActive) {
            overrideChecks++;
            if (overriding) {
                overrideTrue++;
            }
        }
    }

    public static void recordModelPartHook(boolean overridden) {
        if (frameActive) {
            modelPartHooks++;
            if (overridden) {
                modelPartOverrides++;
            }
        }
    }

    public static void recordLionfishPartHook(boolean overridden) {
        if (frameActive) {
            lionfishPartHooks++;
            if (overridden) {
                lionfishPartOverrides++;
            }
        }
    }

    public static void recordBufferHook() {
        if (frameActive) {
            bufferHooks++;
        }
    }

    private static double millis(long nanos) {
        return Math.round((nanos / 1_000_000.0D) * 10.0D) / 10.0D;
    }

    private FrameSpikeDiagnostics() {
    }
}
