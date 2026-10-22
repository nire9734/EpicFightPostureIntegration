package dev.nire9734.epicfightposture.compat.humanoid.client;

import java.util.List;
import java.util.Objects;

/** Pure cache-key values used by Gecko armor conversion. */
final class GeckoArmorCacheKey {
    static SourceIdentity sourceIdentity(Object source) {
        return new SourceIdentity(source);
    }

    static ModelState modelState(List<BoneState> bones) {
        return new ModelState(bones);
    }

    static BoneState boneState(
            boolean hidden,
            boolean hidingChildren,
            float posX,
            float posY,
            float posZ,
            float rotX,
            float rotY,
            float rotZ,
            float scaleX,
            float scaleY,
            float scaleZ,
            float pivotX,
            float pivotY,
            float pivotZ) {
        return new BoneState(
                hidden,
                hidingChildren,
                Float.floatToIntBits(posX),
                Float.floatToIntBits(posY),
                Float.floatToIntBits(posZ),
                Float.floatToIntBits(rotX),
                Float.floatToIntBits(rotY),
                Float.floatToIntBits(rotZ),
                Float.floatToIntBits(scaleX),
                Float.floatToIntBits(scaleY),
                Float.floatToIntBits(scaleZ),
                Float.floatToIntBits(pivotX),
                Float.floatToIntBits(pivotY),
                Float.floatToIntBits(pivotZ)
        );
    }

    static final class SourceIdentity {
        private final Object source;
        private final int hash;

        private SourceIdentity(Object source) {
            this.source = Objects.requireNonNull(source, "source");
            this.hash = System.identityHashCode(source);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof SourceIdentity identity && this.source == identity.source;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    record BoneState(
            boolean hidden,
            boolean hidingChildren,
            int posX,
            int posY,
            int posZ,
            int rotX,
            int rotY,
            int rotZ,
            int scaleX,
            int scaleY,
            int scaleZ,
            int pivotX,
            int pivotY,
            int pivotZ) {
    }

    record ModelState(List<BoneState> bones) {
        ModelState {
            bones = bones == null ? List.of() : List.copyOf(bones);
        }
    }

    private GeckoArmorCacheKey() {
    }
}
