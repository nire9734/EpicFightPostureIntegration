package dev.nire9734.epicfightposture.sync;

import dev.nire9734.epicfightposture.FastReflection;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public final class RuntimeAccess {
    public static int entityId(Object object) {
        return object instanceof Entity entity ? entity.getId() : -1;
    }

    public static UUID entityUuid(Object object) {
        return object instanceof Entity entity ? entity.getUUID() : null;
    }

    public static float health(LivingEntity entity) {
        return entity == null ? 0.0F : entity.getHealth();
    }

    public static float maxHealth(LivingEntity entity) {
        return entity == null ? 1.0F : entity.getMaxHealth();
    }

    public static float bbHeight(LivingEntity entity) {
        return entity == null ? 1.8F : entity.getBbHeight();
    }

    public static Collection<MobEffectInstance> activeEffects(LivingEntity entity) {
        return entity == null ? Collections.emptyList() : entity.getActiveEffects();
    }

    public static Object original(Object object) {
        return FastReflection.invokeNamed(object, new String[]{"getOriginal"});
    }

    public static MobEffect effect(MobEffectInstance instance) {
        return instance == null ? null : instance.getEffect();
    }

    public static boolean isServerEntity(Object object) {
        return object instanceof Entity entity && entity.level() instanceof ServerLevel;
    }

    private RuntimeAccess() {}
}
