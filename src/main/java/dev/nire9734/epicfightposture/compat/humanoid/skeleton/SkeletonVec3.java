package dev.nire9734.epicfightposture.compat.humanoid.skeleton;

/** Immutable three-component vector used by skeleton descriptors. */
public record SkeletonVec3(float x, float y, float z) {
    public static final SkeletonVec3 ZERO = new SkeletonVec3(0.0F, 0.0F, 0.0F);

    public SkeletonVec3 {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("Skeleton vector components must be finite");
        }
    }

    public SkeletonVec3 add(SkeletonVec3 other) {
        return new SkeletonVec3(x + other.x, y + other.y, z + other.z);
    }

    public SkeletonVec3 subtract(SkeletonVec3 other) {
        return new SkeletonVec3(x - other.x, y - other.y, z - other.z);
    }

    public SkeletonVec3 scale(float factor) {
        return new SkeletonVec3(x * factor, y * factor, z * factor);
    }

    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public float distanceTo(SkeletonVec3 other) {
        return subtract(other).length();
    }

    public static SkeletonVec3 midpoint(SkeletonVec3 first, SkeletonVec3 second) {
        return new SkeletonVec3(
                (first.x + second.x) * 0.5F,
                (first.y + second.y) * 0.5F,
                (first.z + second.z) * 0.5F
        );
    }

    public static SkeletonVec3 lerp(SkeletonVec3 first, SkeletonVec3 second, float alpha) {
        return new SkeletonVec3(
                first.x + (second.x - first.x) * alpha,
                first.y + (second.y - first.y) * alpha,
                first.z + (second.z - first.z) * alpha
        );
    }
}
