package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.shelmarow.combat_evolution.ai.CEHumanoidPatch;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import org.slf4j.Logger;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Focused, read-only diagnostics for reported Soul's Weapons compatibility
 * targets. Runs on the server even when no posture state/HUD is created.
 *
 * No reflection, forced stuns, NBT changes or per-entity blacklisting.
 */
public final class SoulsWeaponryDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Set<ResourceLocation> TARGETS = Set.of(
            new ResourceLocation("soulsweapons", "remnant"),
            new ResourceLocation("soulsweapons", "dark_sorcerer"),
            new ResourceLocation("soulsweapons", "forlorn")
    );
    private static final Set<ResourceLocation> DIAGNOSED_SERVER_TYPES =
            ConcurrentHashMap.newKeySet();

    public static boolean isTarget(ResourceLocation id) {
        return TARGETS.contains(id);
    }

    public static void onServerTick(LivingEntity entity) {
        if (entity.tickCount < 20) {
            return;
        }
        ResourceLocation id = entity.getType().builtInRegistryHolder().key().location();
        if (!isTarget(id) || !DIAGNOSED_SERVER_TYPES.add(id)) {
            return;
        }

        try {
            LivingEntityPatch<?> patch =
                    EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
            boolean neutralize = PostureManager.hasNeutralizeAnimation(patch);
            boolean humanoid = patch != null
                    && patch.getArmature() instanceof yesman.epicfight.model.armature.HumanoidArmature;
            boolean customExecution = patch instanceof CustomExecuteEntity;
            boolean eligible = patch != null && !(patch instanceof CEHumanoidPatch)
                    && neutralize && (humanoid || customExecution)
                    && !PostureServerConfig.isBlacklisted(id);
            LOGGER.info(
                    "EFPI souls-diagnostic/server: entity={}, patch={}, armature={}, " +
                    "efpiOwned={}, externallyPatched={}, autoCompatEnabled={}, " +
                    "blacklisted={}, neutralize={}, shortHit={}, longHit={}, " +
                    "distinctNeutralize={}, humanoidArmature={}, customExecution={}, " +
                    "postureEligible={}",
                    id,
                    patch == null ? "none" : patch.getClass().getName(),
                    patch == null ? "none" : describeArmature(patch),
                    VanillaHumanoidCompat.isRegisteredByEfpi(id),
                    VanillaHumanoidCompat.isExternallyPatched(id),
                    VanillaHumanoidCompat.isCompatibilityEnabled(id),
                    PostureServerConfig.isBlacklisted(id),
                    describeHit(patch, StunType.NEUTRALIZE),
                    describeHit(patch, StunType.SHORT),
                    describeHit(patch, StunType.LONG),
                    neutralize, humanoid, customExecution, eligible
            );
        } catch (Throwable ex) {
            LOGGER.warn("EFPI souls-diagnostic/server failed for {}", id, ex);
        }
    }

    public static void onBreakApplied(
            LivingEntity entity, LivingEntityPatch<?> patch, boolean accepted) {
        ResourceLocation id = entity.getType().builtInRegistryHolder().key().location();
        if (!isTarget(id)) {
            return;
        }
        try {
            var animator = patch.getAnimator();
            var player = animator.getPlayerFor(null);
            var real = player == null ? null : player.getRealAnimation();
            var expected = patch.getHitAnimation(StunType.NEUTRALIZE);
            LOGGER.info(
                    "EFPI souls-diagnostic/break: entity={}, accepted={}, patch={}, " +
                    "animation={}, expected={}, animationMatches={}, isStunned={}",
                    id, accepted, patch.getClass().getName(),
                    real == null ? "null" : String.valueOf(real.registryName()),
                    expected == null ? "null" : String.valueOf(expected.registryName()),
                    real != null && real == expected, patch.isStunned());
        } catch (Throwable ex) {
            LOGGER.warn("EFPI souls-diagnostic/break inspection failed for {}", id, ex);
        }
    }

    private static String describeArmature(LivingEntityPatch<?> patch) {
        try {
            var a = patch.getArmature();
            return a == null ? "null" : a.getClass().getName();
        } catch (Throwable ex) {
            return "error:" + ex.getClass().getSimpleName();
        }
    }

    private static String describeHit(LivingEntityPatch<?> patch, StunType type) {
        if (patch == null) {
            return "no-patch";
        }
        try {
            AssetAccessor<? extends StaticAnimation> a = patch.getHitAnimation(type);
            return a == null ? "null" : String.valueOf(a.registryName())
                    + (a.isEmpty() ? "(empty)" : "");
        } catch (Throwable ex) {
            return "error:" + ex.getClass().getSimpleName();
        }
    }

    private SoulsWeaponryDiagnostics() {}
}
