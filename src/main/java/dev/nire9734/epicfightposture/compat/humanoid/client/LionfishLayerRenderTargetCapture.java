package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.slf4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Captures the RenderType selected by a source RenderLayer without replacing its
 * MultiBufferSource or VertexConsumer contract.
 *
 * <p>Lionfish source models emit QUADS, while EFPI SkinnedMesh data is triangulated.
 * Replaying a triangulated mesh directly into a source QUADS VertexConsumer corrupts
 * primitive assembly. Bridges resolve the original RenderType through this scope and
 * render via SkinnedMesh.draw(), allowing Epic Fight to build an equivalent
 * triangulated RenderType while preserving the layer's shader/texture/state.</p>
 */
public final class LionfishLayerRenderTargetCapture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    static Scope open(MultiBufferSource bufferSource) {
        Frame previous = ACTIVE.get();
        if (bufferSource == null) {
            return new Scope(previous, false);
        }
        ACTIVE.set(new Frame(bufferSource));
        return new Scope(previous, true);
    }

    /** Called from the vanilla BufferSource mixin after getBuffer(RenderType). */
    public static void record(
            MultiBufferSource bufferSource,
            RenderType renderType,
            VertexConsumer vertexConsumer) {
        Frame frame = ACTIVE.get();
        if (frame == null) {
            return;
        }
        frame.recordCalls++;
        if (frame.bufferSource != bufferSource) {
            frame.mismatchedBufferSourceRecords++;
            frame.lastMismatchedBufferSource = bufferSource;
            frame.lastMismatchedRenderType = renderType;
            frame.lastMismatchedConsumer = vertexConsumer;
            return;
        }
        if (renderType == null || vertexConsumer == null) {
            frame.invalidRecords++;
            return;
        }
        frame.renderTypes.put(vertexConsumer, renderType);
        LionfishRetargetDiagnostics.renderTarget(vertexConsumer, renderType);
    }

    static Target resolve(VertexConsumer vertexConsumer) {
        Frame frame = ACTIVE.get();
        if (frame == null || vertexConsumer == null) {
            return null;
        }
        RenderType renderType = frame.renderTypes.get(vertexConsumer);
        if (renderType != null) {
            return new Target(frame.bufferSource, renderType);
        }

        LOGGER.info(
                "[EFPI-DIAG] RENDER_TARGET_MISS consumer={} activeSource={} recordCalls={} captured={} invalidRecords={} mismatchRecords={} lastMismatchSource={} lastMismatchConsumer={} lastMismatchRenderType={} capturedTargets={}",
                id(vertexConsumer),
                id(frame.bufferSource),
                frame.recordCalls,
                frame.renderTypes.size(),
                frame.invalidRecords,
                frame.mismatchedBufferSourceRecords,
                id(frame.lastMismatchedBufferSource),
                id(frame.lastMismatchedConsumer),
                String.valueOf(frame.lastMismatchedRenderType),
                capturedTargets(frame)
        );
        return null;
    }

    private static String capturedTargets(Frame frame) {
        if (frame.renderTypes.isEmpty()) {
            return "[]";
        }
        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<VertexConsumer, RenderType> entry : frame.renderTypes.entrySet()) {
            if (!first) result.append(';');
            first = false;
            result.append(id(entry.getKey())).append("->").append(entry.getValue());
        }
        return result.append(']').toString();
    }

    private static String id(Object value) {
        if (value == null) {
            return "null";
        }
        return value.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(value));
    }

    record Target(MultiBufferSource bufferSource, RenderType renderType) {
    }

    static final class Scope implements AutoCloseable {
        private final Frame previous;
        private final boolean installed;
        private boolean closed;

        private Scope(Frame previous, boolean installed) {
            this.previous = previous;
            this.installed = installed;
        }

        @Override
        public void close() {
            if (closed || !installed) {
                closed = true;
                return;
            }
            closed = true;
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    private static final class Frame {
        private final MultiBufferSource bufferSource;
        private final IdentityHashMap<VertexConsumer, RenderType> renderTypes = new IdentityHashMap<>();
        private int recordCalls;
        private int invalidRecords;
        private int mismatchedBufferSourceRecords;
        private MultiBufferSource lastMismatchedBufferSource;
        private RenderType lastMismatchedRenderType;
        private VertexConsumer lastMismatchedConsumer;

        private Frame(MultiBufferSource bufferSource) {
            this.bufferSource = bufferSource;
        }
    }

    private LionfishLayerRenderTargetCapture() {
    }
}
