package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.entitypatch.CustomHumanoidMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fail-closed, server-only fallback for Epic Fight 21.17 custom humanoids.
 * This does NOT replace entity patches, AI, renderers, existing stun tables,
 * or datapack-authored neutralize animations.
 */
public final class NeoForgeMissingHumanoidNeutralize {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> LOGGED_SUCCESS = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> LOGGED_FAILURE = ConcurrentHashMap.newKeySet();
    private static final Field PROVIDER_FIELD = findProviderField();

    private static Field findProviderField() {
        try {
            Field field = CustomHumanoidMobPatch.class.getDeclaredField("provider");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("EFPI NeoForge custom humanoid neutralize fallback unavailable: provider field inaccessible",
                    exception);
            return null;
        }
    }

    public static void installIfNeeded(LivingEntityPatch<?> patch) {
        if (!(patch instanceof CustomHumanoidMobPatch<?>)
                || PROVIDER_FIELD == null) return;
        LivingEntity entity = patch.getOriginal();
        if (entity == null || entity.level().isClientSide()) return;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null || NeoForgePostureConfig.ENTITY_BLACKLIST.get().contains(id.toString())) return;

        try {
            if (!(patch.getArmature() instanceof HumanoidArmature)) return;
            AssetAccessor<? extends StaticAnimation> current = patch.getHitAnimation(StunType.NEUTRALIZE);
            if (available(current)) return;
            if (!available(patch.getHitAnimation(StunType.SHORT))
                    && !available(patch.getHitAnimation(StunType.LONG))) return;

            AnimationAccessor<? extends StaticAnimation> fallback = Animations.BIPED_COMMON_NEUTRALIZED;
            if (!available(fallback)) {
                warnOnce(id, "canonical biped neutralize animation is unavailable", null);
                return;
            }
            Object data = PROVIDER_FIELD.get(patch);
            if (!(data instanceof MobPatchReloadListener.CustomHumanoidMobPatchProvider provider)) {
                warnOnce(id, "unexpected custom humanoid provider type", null);
                return;
            }
            Map<StunType, AnimationAccessor<? extends StaticAnimation>> animations =
                    provider.getStunAnimations();
            if (animations == null) {
                warnOnce(id, "missing datapack stun animation map", null);
                return;
            }
            if (available(animations.get(StunType.NEUTRALIZE))) return;
            animations.put(StunType.NEUTRALIZE, fallback);
            if (patch.getHitAnimation(StunType.NEUTRALIZE) == fallback) {
                if (LOGGED_SUCCESS.add(id)) {
                    LOGGER.debug("EFPI NeoForge installed missing humanoid NEUTRALIZE for {}", id);
                }
            } else {
                warnOnce(id, "provider update did not become authoritative", null);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOnce(id, "failed to install custom humanoid neutralize", exception);
        }
    }

    private static boolean available(AssetAccessor<? extends StaticAnimation> accessor) {
        return accessor != null && !accessor.isEmpty();
    }

    private static void warnOnce(ResourceLocation id, String reason, Throwable cause) {
        if (!LOGGED_FAILURE.add(id)) return;
        if (cause == null) LOGGER.warn("EFPI NeoForge neutralize fallback skipped {}: {}", id, reason);
        else LOGGER.warn("EFPI NeoForge neutralize fallback skipped {}: {}", id, reason, cause);
    }

    private NeoForgeMissingHumanoidNeutralize() {}
}
