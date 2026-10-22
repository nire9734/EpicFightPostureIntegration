package dev.nire9734.epicfightposture.neoforge.compat.models;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.IdentityHashMap;

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
        if (frame == null
                || frame.bufferSource != bufferSource
                || renderType == null
                || vertexConsumer == null) {
            return;
        }
        frame.renderTypes.put(vertexConsumer, renderType);
    }

    static Target resolve(VertexConsumer vertexConsumer) {
        Frame frame = ACTIVE.get();
        if (frame == null || vertexConsumer == null) {
            return null;
        }
        RenderType renderType = frame.renderTypes.get(vertexConsumer);
        return renderType == null ? null : new Target(frame.bufferSource, renderType);
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

        private Frame(MultiBufferSource bufferSource) {
            this.bufferSource = bufferSource;
        }
    }

    private LionfishLayerRenderTargetCapture() {
    }
}
