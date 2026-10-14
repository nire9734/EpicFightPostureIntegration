package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

/** Immutable quaternion used by skeleton descriptors. */
public record SkeletonQuat(float x, float y, float z, float w) {
    private static final float EPSILON = 1.0E-8F;
    public static final SkeletonQuat IDENTITY = new SkeletonQuat(0.0F, 0.0F, 0.0F, 1.0F, false);

    public SkeletonQuat(float x, float y, float z, float w) {
        this(x, y, z, w, true);
    }

    private SkeletonQuat(float x, float y, float z, float w, boolean normalize) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w)) {
            throw new IllegalArgumentException("Skeleton quaternion components must be finite");
        }

        if (normalize) {
            float lengthSquared = x * x + y * y + z * z + w * w;
            if (lengthSquared <= EPSILON) {
                this.x = 0.0F;
                this.y = 0.0F;
                this.z = 0.0F;
                this.w = 1.0F;
                return;
            }
            float inverseLength = 1.0F / (float) Math.sqrt(lengthSquared);
            this.x = x * inverseLength;
            this.y = y * inverseLength;
            this.z = z * inverseLength;
            this.w = w * inverseLength;
        } else {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }
    }

    /** Quaternion composition using the conventional {@code this * other} order. */
    public SkeletonQuat multiply(SkeletonQuat other) {
        return new SkeletonQuat(
                w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z
        );
    }

    public SkeletonQuat inverse() {
        return new SkeletonQuat(-x, -y, -z, w, false);
    }

    /** Rotates a vector by this normalized quaternion. */
    public SkeletonVec3 rotate(SkeletonVec3 vector) {
        float vx = vector.x();
        float vy = vector.y();
        float vz = vector.z();

        float tx = 2.0F * (y * vz - z * vy);
        float ty = 2.0F * (z * vx - x * vz);
        float tz = 2.0F * (x * vy - y * vx);

        return new SkeletonVec3(
                vx + w * tx + (y * tz - z * ty),
                vy + w * ty + (z * tx - x * tz),
                vz + w * tz + (x * ty - y * tx)
        );
    }
}
