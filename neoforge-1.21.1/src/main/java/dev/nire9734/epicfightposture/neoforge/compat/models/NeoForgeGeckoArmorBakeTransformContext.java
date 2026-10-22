package dev.nire9734.epicfightposture.neoforge.compat.models;

import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Temporary bind-space context used while converting one Gecko armor callback
 * into an Epic Fight skinned mesh.
 *
 * <p>{@code ItemArmorGeoLayer} renders armor from the source entity bone's
 * already accumulated matrix. The armor mesh baker normally starts at model
 * origin instead, so it needs the source bone and Gecko's per-callback fit scale
 * in order to reconstruct that original bind-space matrix composition.</p>
 */
public final class NeoForgeGeckoArmorBakeTransformContext {
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    public static Scope open(GeoBone sourceBone, Vector3f fitScale) {
        State previous = ACTIVE.get();
        if (sourceBone == null || fitScale == null
                || !finitePositive(fitScale.x)
                || !finitePositive(fitScale.y)
                || !finitePositive(fitScale.z)) {
            return new Scope(previous, false);
        }

        ACTIVE.set(new State(
                sourceBone,
                fitScale.x,
                fitScale.y,
                fitScale.z
        ));
        return new Scope(previous, true);
    }

    static State current() {
        return ACTIVE.get();
    }

    private static boolean finitePositive(float value) {
        return Float.isFinite(value) && value > 1.0E-6F;
    }

    static final class State {
        private final GeoBone sourceBone;
        private final float scaleX;
        private final float scaleY;
        private final float scaleZ;

        private State(GeoBone sourceBone, float scaleX, float scaleY, float scaleZ) {
            this.sourceBone = sourceBone;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
        }

        GeoBone sourceBone() {
            return sourceBone;
        }

        float scaleX() {
            return scaleX;
        }

        float scaleY() {
            return scaleY;
        }

        float scaleZ() {
            return scaleZ;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final State previous;
        private final boolean installed;
        private boolean closed;

        private Scope(State previous, boolean installed) {
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

    private NeoForgeGeckoArmorBakeTransformContext() {
    }
}
