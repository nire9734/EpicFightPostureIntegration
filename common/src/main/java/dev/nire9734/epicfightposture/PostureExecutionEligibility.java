package dev.nire9734.epicfightposture;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.model.armature.SpiderArmature;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

/**
 * Shared, read-only eligibility checks for Forge and NeoForge posture support.
 * These checks must never start an animation or apply a stun to probe a mob.
 * A successful preflight is necessary, but cannot guarantee that another mod
 * will allow a future NEUTRALIZE stun during every combat state.
 */
public final class PostureExecutionEligibility {
    public enum Status { SUPPORTED, UNSUPPORTED, PENDING }

    public enum Reason {
        READY,
        TARGET_NOT_ALIVE,
        PLAYER_EXCLUDED,
        ENTITY_BLACKLISTED,
        CE_OWNED_PATCH,
        SPIDER_EXECUTION_UNAVAILABLE,
        MISSING_PATCH,
        PATCH_NOT_INITIALIZED,
        MISSING_NEUTRALIZE,
        INVALID_NEUTRALIZE_ANIMATION,
        SHARED_STUN_ANIMATION,
        INCOMPATIBLE_ARMATURE,
        INVALID_ARMATURE_STRUCTURE,
        ANIMATOR_NOT_READY,
        ANIMATION_PLAYER_NOT_READY
    }

    public record Result(Status status, Reason reason) {
        public boolean supported() {
            return status == Status.SUPPORTED;
        }
    }

    /** Old admission versus the current stricter preflight, on the same patch. */
    public record Comparison(boolean legacyEligible, Result current) {}

    private static final Result READY = new Result(Status.SUPPORTED, Reason.READY);

    /**
     * The pre-preflight (3.1.x-style) decision is only evaluated for reporting.
     * It must never be used to enable posture on the server.
     *
     * @param baseExclusion a loader-specific exclusion applying to both versions,
     *                      or null if the mob is eligible for further checks
     * @param spiderCompatible a new preflight-only restriction for EFPI spider patches
     */
    public static Comparison compare(LivingEntityPatch<?> patch, boolean initialized,
                                     boolean customExecution, Reason baseExclusion,
                                     boolean spiderCompatible) {
        if (baseExclusion != null) {
            return new Comparison(false, unsupported(baseExclusion));
        }
        boolean legacy = legacyEligible(patch, customExecution);
        Result current = spiderCompatible
                ? inspect(patch, initialized, customExecution)
                : unsupported(Reason.SPIDER_EXECUTION_UNAVAILABLE);
        return new Comparison(legacy, current);
    }

    /** Mirrors the former Forge/NeoForge predicate, without the new preflight. */
    private static boolean legacyEligible(LivingEntityPatch<?> patch, boolean customExecution) {
        if (patch == null) return false;
        try {
            AssetAccessor<? extends StaticAnimation> neutralize = patch.getHitAnimation(StunType.NEUTRALIZE);
            if (!available(neutralize)
                    || sameAnimation(neutralize, patch.getHitAnimation(StunType.SHORT))
                    || sameAnimation(neutralize, patch.getHitAnimation(StunType.LONG))) {
                return false;
            }
            return customExecution || patch.getArmature() instanceof HumanoidArmature;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Called after the loader's optional datapack NEUTRALIZE fallback has run.
     * The caller supplies its loader-specific patch initialization state.
     * CustomExecuteEntity implementations keep their own execution contract;
     * they are not forced to use a humanoid armature.
     */
    public static Result inspect(LivingEntityPatch<?> patch, boolean initialized, boolean customExecution) {
        if (patch == null) return unsupported(Reason.MISSING_PATCH);
        if (!initialized) return pending(Reason.PATCH_NOT_INITIALIZED);

        final AssetAccessor<? extends StaticAnimation> neutralize;
        try {
            neutralize = patch.getHitAnimation(StunType.NEUTRALIZE);
            if (!available(neutralize)) return unsupported(Reason.MISSING_NEUTRALIZE);
            if (neutralize.get().isInvalid()) return unsupported(Reason.INVALID_NEUTRALIZE_ANIMATION);
            if (sameAnimation(neutralize, patch.getHitAnimation(StunType.SHORT))
                    || sameAnimation(neutralize, patch.getHitAnimation(StunType.LONG))) {
                return unsupported(Reason.SHARED_STUN_ANIMATION);
            }
        } catch (RuntimeException | LinkageError ignored) {
            return unsupported(Reason.MISSING_NEUTRALIZE);
        }

        final Armature armature;
        try {
            armature = patch.getArmature();
        } catch (RuntimeException | LinkageError ignored) {
            return unsupported(Reason.INCOMPATIBLE_ARMATURE);
        }
        if (!(armature instanceof HumanoidArmature) && !customExecution) {
            return unsupported(Reason.INCOMPATIBLE_ARMATURE);
        }
        // The EFPI biped/spider animation paths require a usable Epic Fight
        // joint tree. Do not constrain arbitrary external CustomExecuteEntity
        // implementations to the built-in biped/spider skeleton layouts.
        if ((armature instanceof HumanoidArmature || armature instanceof SpiderArmature)
                && !validJointHierarchy(armature.rootJoint, armature.getJointNumber())) {
            return unsupported(Reason.INVALID_ARMATURE_STRUCTURE);
        }

        try {
            var animator = patch.getAnimator();
            if (animator == null) return pending(Reason.ANIMATOR_NOT_READY);
            if (animator.getPlayerFor(null) == null) return pending(Reason.ANIMATION_PLAYER_NOT_READY);
        } catch (RuntimeException | LinkageError ignored) {
            return pending(Reason.ANIMATOR_NOT_READY);
        }
        return READY;
    }

    /** Check the animation reference independently of patch/Animator readiness. */
    public static boolean hasDistinctNeutralize(LivingEntityPatch<?> patch) {
        if (patch == null) return false;
        try {
            AssetAccessor<? extends StaticAnimation> neutralize = patch.getHitAnimation(StunType.NEUTRALIZE);
            return available(neutralize)
                    && !neutralize.get().isInvalid()
                    && !sameAnimation(neutralize, patch.getHitAnimation(StunType.SHORT))
                    && !sameAnimation(neutralize, patch.getHitAnimation(StunType.LONG));
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Conservative geometry checks, without requiring any particular joint
     * names or counts (which differ across legitimate modded armatures).
     */
    static boolean validJointHierarchy(Joint root, int declaredJointCount) {
        if (root == null || declaredJointCount <= 0) return false;
        IdentityHashMap<Joint, Boolean> visited = new IdentityHashMap<>();
        Set<Integer> ids = new HashSet<>();
        ArrayDeque<Joint> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Joint joint = pending.removeLast();
            if (visited.put(joint, Boolean.TRUE) != null || !ids.add(joint.getId())
                    || joint.getId() < 0 || joint.getId() >= declaredJointCount
                    || visited.size() > declaredJointCount || !finite(joint.getLocalTransform())) {
                return false;
            }
            if (joint.getSubJoints() == null) return false;
            for (Joint child : joint.getSubJoints()) {
                if (child == null) return false;
                pending.add(child);
            }
        }
        return !ids.isEmpty();
    }

    private static boolean finite(OpenMatrix4f m) {
        return m != null && Float.isFinite(m.m00) && Float.isFinite(m.m01)
                && Float.isFinite(m.m02) && Float.isFinite(m.m03)
                && Float.isFinite(m.m10) && Float.isFinite(m.m11)
                && Float.isFinite(m.m12) && Float.isFinite(m.m13)
                && Float.isFinite(m.m20) && Float.isFinite(m.m21)
                && Float.isFinite(m.m22) && Float.isFinite(m.m23)
                && Float.isFinite(m.m30) && Float.isFinite(m.m31)
                && Float.isFinite(m.m32) && Float.isFinite(m.m33);
    }

    private static boolean available(AssetAccessor<? extends StaticAnimation> animation) {
        return animation != null && !animation.isEmpty();
    }

    private static boolean sameAnimation(AssetAccessor<? extends StaticAnimation> first,
                                         AssetAccessor<? extends StaticAnimation> second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        try {
            return first.registryName() != null && first.registryName().equals(second.registryName());
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Result unsupported(Reason reason) {
        return new Result(Status.UNSUPPORTED, reason);
    }

    private static Result pending(Reason reason) {
        return new Result(Status.PENDING, reason);
    }

    private PostureExecutionEligibility() {}
}
