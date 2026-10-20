package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.PostureServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structural compatibility for datapack-provided Epic Fight humanoid patches.
 * CustomHumanoidMobPatch#getHitAnimation reads its stun animation table without
 * inheriting HumanoidMobPatch's fallback for missing NEUTRALIZE. Preserve any
 * existing override and supply the canonical humanoid guard-break animation
 * only for actual humanoid patches with configured ordinary hit reactions.
 */
public final class MissingHumanoidNeutralize {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> REPORTED = ConcurrentHashMap.newKeySet();

    public static AssetAccessor<? extends StaticAnimation> resolve(
            LivingEntityPatch<?> patch,
            StunType stunType,
            AssetAccessor<? extends StaticAnimation> originalAnimation) {
        if (stunType != StunType.NEUTRALIZE || isPresent(originalAnimation) || patch == null) {
            return null;
        }

        try {
            LivingEntity entity = patch.getOriginal();
            if (entity == null || !(patch.getArmature() instanceof HumanoidArmature)) {
                return null;
            }
            ResourceLocation id = EntityType.getKey(entity.getType());
            if (id == null || PostureServerConfig.isBlacklisted(id)) {
                return null;
            }
            // Do not opt a custom boss with *no* hit reactions into forced stuns.
            // Datapack humanoids with at least one ordinary stun animation have
            // already declared that their patched model supports hit reactions.
            boolean ordinaryHitReaction = isPresent(patch.getHitAnimation(StunType.SHORT))
                    || isPresent(patch.getHitAnimation(StunType.LONG));
            if (!ordinaryHitReaction
                    || !isPresent(Animations.BIPED_COMMON_NEUTRALIZED)) {
                return null;
            }

            if (REPORTED.add(id)) {
                LOGGER.info(
                        "EFPI generic humanoid neutralize fallback ACTIVE: entity={}, patch={}, " +
                        "armature={}, fallback={}, shortOrLongPresent=true",
                        id, patch.getClass().getName(),
                        patch.getArmature().getClass().getSimpleName(),
                        Animations.BIPED_COMMON_NEUTRALIZED.registryName());
            }
            return Animations.BIPED_COMMON_NEUTRALIZED;
        } catch (Throwable ex) {
            LOGGER.warn("EFPI generic humanoid neutralize fallback inspection failed for {}",
                    patch.getClass().getName(), ex);
            return null;
        }
    }

    private static boolean isPresent(AssetAccessor<? extends StaticAnimation> animation) {
        return animation != null && !animation.isEmpty();
    }

    private MissingHumanoidNeutralize() {}
}
