package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.PostureServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.world.capabilities.entitypatch.CustomHumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fills missing *data* in the Epic Fight datapack-backed patch provider.
 * CustomHumanoidMobPatch.getHitAnimation reads the provider's stunAnimations map
 * directly, so installing a single missing entry is visible to both Epic Fight
 * applyStun and Combat Evolution's guard-break/execution checks.
 *
 * This does not replace an existing patch, renderer, AI or datapack-supplied
 * NEUTRALIZE. It does not suppress HUDs after transient stun failures.
 */
public final class MissingHumanoidNeutralize {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> LOGGED_SUCCESS = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> LOGGED_FAILURE = ConcurrentHashMap.newKeySet();

    private static final Field PROVIDER_FIELD = findProviderField();

    private static Field findProviderField() {
        try {
            Field field = CustomHumanoidMobPatch.class.getDeclaredField("provider");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.error("EFPI generic humanoid fallback cannot access Epic Fight custom patch provider", ex);
            return null;
        }
    }

    public static void installIfNeeded(LivingEntityPatch<?> patch) {
        if (!(patch instanceof CustomHumanoidMobPatch<?>) || PROVIDER_FIELD == null) {
            return;
        }

        LivingEntity entity = patch.getOriginal();
        if (entity == null) {
            return;
        }
        ResourceLocation id = EntityType.getKey(entity.getType());
        if (id == null || PostureServerConfig.isBlacklisted(id)) {
            return;
        }

        try {
            if (!(patch.getArmature() instanceof HumanoidArmature)) {
                return;
            }

            // The datapack author explicitly supplied a usable guard break;
            // never overwrite it.
            if (isPresent(patch.getHitAnimation(StunType.NEUTRALIZE))) {
                return;
            }
            if (!isPresent(patch.getHitAnimation(StunType.SHORT))
                    && !isPresent(patch.getHitAnimation(StunType.LONG))) {
                return;
            }
            AnimationAccessor<? extends StaticAnimation> fallback =
                    Animations.BIPED_COMMON_NEUTRALIZED;
            if (!isPresent(fallback)) {
                warnOnce(id, "canonical BIPED_COMMON_NEUTRALIZED animation is unavailable", null);
                return;
            }

            Object rawProvider = PROVIDER_FIELD.get(patch);
            if (!(rawProvider instanceof MobPatchReloadListener.CustomHumanoidMobPatchProvider provider)) {
                warnOnce(id, "unexpected CustomHumanoidMobPatch provider type", null);
                return;
            }

            Map<StunType, AnimationAccessor<? extends StaticAnimation>> animations =
                    provider.getStunAnimations();
            if (animations == null) {
                warnOnce(id, "null stun animation map in custom patch provider", null);
                return;
            }
            AnimationAccessor<? extends StaticAnimation> existing =
                    animations.get(StunType.NEUTRALIZE);
            if (isPresent(existing)) {
                return;
            }

            // The provider's table is the single source read by the patch.
            // We add only the missing canonical guard-break animation.
            animations.put(StunType.NEUTRALIZE, fallback);

            boolean effective = patch.getHitAnimation(StunType.NEUTRALIZE) == fallback;
            if (effective && LOGGED_SUCCESS.add(id)) {
                LOGGER.info(
                        "EFPI generic humanoid neutralize fallback ACTIVE: entity={}, patch={}, " +
                        "animation={}, providerPatched=true",
                        id, patch.getClass().getName(), fallback.registryName());
            } else if (!effective) {
                warnOnce(id, "provider map updated but getHitAnimation did not return the fallback", null);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            warnOnce(id, "cannot safely update custom humanoid stun animation map", ex);
        }
    }

    private static boolean isPresent(yesman.epicfight.api.asset.AssetAccessor<? extends StaticAnimation> a) {
        return a != null && !a.isEmpty();
    }

    private static void warnOnce(ResourceLocation id, String reason, Throwable cause) {
        if (!LOGGED_FAILURE.add(id)) {
            return;
        }
        if (cause == null) {
            LOGGER.warn("EFPI generic humanoid neutralize fallback REJECTED: entity={}, reason={}", id, reason);
        } else {
            LOGGER.warn("EFPI generic humanoid neutralize fallback REJECTED: entity={}, reason={}", id, reason, cause);
        }
    }

    private MissingHumanoidNeutralize() {}
}
