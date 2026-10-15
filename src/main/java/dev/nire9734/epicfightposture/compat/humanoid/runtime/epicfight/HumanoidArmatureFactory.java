package dev.nire9734.epicfightposture.compat.humanoid.runtime.epicfight;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.model.armature.HumanoidArmature;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a target-proportioned Epic Fight humanoid armature while preserving the
 * canonical BIPED joint names, ids, hierarchy, and model-space joint axes.
 *
 * <p>This class is intentionally kept in the Epic Fight runtime compatibility
 * layer. The portable humanoid rig/skeleton/weight pipeline does not depend on
 * Epic Fight armature or asset APIs.</p>
 */
public final class HumanoidArmatureFactory {
    private static final float EPSILON = 1.0E-8F;

    public static HumanoidArmature create(
            String name,
            HumanoidSkeletonDescriptor descriptor,
            HumanoidArmature canonical) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(canonical, "canonical");

        Map<String, OpenMatrix4f> canonicalModel = new HashMap<>();
        collectModelTransforms(canonical.rootJoint, new OpenMatrix4f(), canonicalModel);

        Map<String, OpenMatrix4f> targetModel = new HashMap<>();
        populateTargetModelTransforms(canonical.rootJoint, descriptor, canonicalModel, targetModel);

        Map<String, Joint> targetJoints = new LinkedHashMap<>();
        Joint targetRoot = copyHierarchy(
                canonical.rootJoint,
                null,
                targetModel,
                targetJoints
        );

        HumanoidArmature result = new HumanoidArmature(
                name,
                canonical.getJointNumber(),
                targetRoot,
                targetJoints
        );
        result.bakeOriginMatrices();
        return result;
    }

    /** Creates a non-registry accessor suitable for Armatures.registerEntityTypeArmature. */
    public static AssetAccessor<HumanoidArmature> accessor(
            ResourceLocation id,
            HumanoidArmature prototype) {
        return new RuntimeArmatureAccessor(id, prototype);
    }

    private static void collectModelTransforms(
            Joint joint,
            OpenMatrix4f parentModel,
            Map<String, OpenMatrix4f> output) {
        OpenMatrix4f model = OpenMatrix4f.mul(parentModel, joint.getLocalTransform(), null);
        output.put(joint.getName(), model);
        for (Joint child : joint.getSubJoints()) {
            collectModelTransforms(child, model, output);
        }
    }

    private static void populateTargetModelTransforms(
            Joint joint,
            HumanoidSkeletonDescriptor descriptor,
            Map<String, OpenMatrix4f> canonicalModel,
            Map<String, OpenMatrix4f> output) {
        OpenMatrix4f canonical = required(canonicalModel, joint.getName());
        SkeletonVec3 targetPosition = targetPosition(joint.getName(), descriptor, canonicalModel);

        // Preserve canonical BIPED model-space axes. Only the pivot position moves.
        // This makes existing BIPED animation rotation deltas bend around the target
        // model's joints without importing arbitrary source-model local axes.
        OpenMatrix4f target = new OpenMatrix4f(canonical);
        target.m30 = targetPosition.x();
        target.m31 = targetPosition.y();
        target.m32 = targetPosition.z();
        output.put(joint.getName(), target);

        for (Joint child : joint.getSubJoints()) {
            populateTargetModelTransforms(child, descriptor, canonicalModel, output);
        }
    }

    private static Joint copyHierarchy(
            Joint source,
            OpenMatrix4f parentTargetModel,
            Map<String, OpenMatrix4f> targetModel,
            Map<String, Joint> output) {
        OpenMatrix4f model = required(targetModel, source.getName());
        OpenMatrix4f local;
        if (parentTargetModel == null) {
            local = new OpenMatrix4f(model);
        } else {
            OpenMatrix4f inverseParent = OpenMatrix4f.invert(parentTargetModel, null);
            if (inverseParent == null) {
                throw new IllegalStateException("Non-invertible target parent transform for " + source.getName());
            }
            local = OpenMatrix4f.mul(inverseParent, model, null);
        }

        Joint target = new Joint(source.getName(), source.getId(), local);
        output.put(target.getName(), target);
        for (Joint child : source.getSubJoints()) {
            target.addSubJoints(copyHierarchy(child, model, targetModel, output));
        }
        return target;
    }

    private static SkeletonVec3 targetPosition(
            String jointName,
            HumanoidSkeletonDescriptor descriptor,
            Map<String, OpenMatrix4f> canonicalModel) {
        return switch (jointName) {
            case "Root" -> position(descriptor, HumanoidJoint.ROOT);

            case "Thigh_R" -> position(descriptor, HumanoidJoint.HIP_RIGHT);
            case "Leg_R", "Knee_R" -> position(descriptor, HumanoidJoint.KNEE_RIGHT);
            case "Thigh_L" -> position(descriptor, HumanoidJoint.HIP_LEFT);
            case "Leg_L", "Knee_L" -> position(descriptor, HumanoidJoint.KNEE_LEFT);

            case "Torso" -> position(descriptor, HumanoidJoint.SPINE);
            case "Chest" -> position(descriptor, HumanoidJoint.CHEST);
            case "Head" -> position(descriptor, HumanoidJoint.HEAD);

            case "Arm_R" -> position(descriptor, HumanoidJoint.SHOULDER_RIGHT);
            case "Hand_R", "Elbow_R" -> position(descriptor, HumanoidJoint.ELBOW_RIGHT);
            case "Tool_R" -> position(descriptor, HumanoidJoint.WRIST_RIGHT);
            case "Shoulder_R" -> shoulderHelper(
                    descriptor,
                    canonicalModel,
                    "Shoulder_R",
                    "Arm_R",
                    HumanoidJoint.SHOULDER_RIGHT
            );

            case "Arm_L" -> position(descriptor, HumanoidJoint.SHOULDER_LEFT);
            case "Hand_L", "Elbow_L" -> position(descriptor, HumanoidJoint.ELBOW_LEFT);
            case "Tool_L" -> position(descriptor, HumanoidJoint.WRIST_LEFT);
            case "Shoulder_L" -> shoulderHelper(
                    descriptor,
                    canonicalModel,
                    "Shoulder_L",
                    "Arm_L",
                    HumanoidJoint.SHOULDER_LEFT
            );

            default -> fromMatrix(required(canonicalModel, jointName));
        };
    }

    /**
     * Shoulder_R/L are Epic Fight helper joints; Arm_R/L are the actual upper-arm
     * deformation pivots. Keep the helper at the same canonical fraction between
     * Chest and the semantic shoulder rather than treating it as the shoulder itself.
     */
    private static SkeletonVec3 shoulderHelper(
            HumanoidSkeletonDescriptor descriptor,
            Map<String, OpenMatrix4f> canonicalModel,
            String helperName,
            String armName,
            HumanoidJoint semanticShoulder) {
        SkeletonVec3 canonicalChest = fromMatrix(required(canonicalModel, "Chest"));
        SkeletonVec3 canonicalHelper = fromMatrix(required(canonicalModel, helperName));
        SkeletonVec3 canonicalArm = fromMatrix(required(canonicalModel, armName));

        SkeletonVec3 canonicalSegment = canonicalArm.subtract(canonicalChest);
        SkeletonVec3 helperOffset = canonicalHelper.subtract(canonicalChest);
        float denominator = canonicalSegment.lengthSquared();
        float fraction = denominator <= EPSILON
                ? 0.5F
                : dot(helperOffset, canonicalSegment) / denominator;
        fraction = Math.max(0.0F, Math.min(1.0F, fraction));

        SkeletonVec3 targetChest = position(descriptor, HumanoidJoint.CHEST);
        SkeletonVec3 targetShoulder = position(descriptor, semanticShoulder);
        return SkeletonVec3.lerp(targetChest, targetShoulder, fraction);
    }

    private static float dot(SkeletonVec3 first, SkeletonVec3 second) {
        return first.x() * second.x() + first.y() * second.y() + first.z() * second.z();
    }

    private static SkeletonVec3 position(
            HumanoidSkeletonDescriptor descriptor,
            HumanoidJoint joint) {
        return descriptor.bindModelPosition(joint);
    }

    private static SkeletonVec3 fromMatrix(OpenMatrix4f matrix) {
        return new SkeletonVec3(matrix.m30, matrix.m31, matrix.m32);
    }

    private static OpenMatrix4f required(Map<String, OpenMatrix4f> transforms, String name) {
        OpenMatrix4f matrix = transforms.get(name);
        if (matrix == null) {
            throw new IllegalArgumentException("Canonical humanoid armature is missing joint " + name);
        }
        return matrix;
    }

    private record RuntimeArmatureAccessor(
            ResourceLocation registryName,
            HumanoidArmature prototype
    ) implements AssetAccessor<HumanoidArmature> {
        private RuntimeArmatureAccessor {
            Objects.requireNonNull(registryName, "registryName");
            Objects.requireNonNull(prototype, "prototype");
        }

        @Override
        public HumanoidArmature get() {
            return prototype;
        }

        @Override
        public boolean inRegistry() {
            return false;
        }
    }

    private HumanoidArmatureFactory() {
    }
}
