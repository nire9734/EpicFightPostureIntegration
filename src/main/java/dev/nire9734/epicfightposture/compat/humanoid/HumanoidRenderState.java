package dev.nire9734.epicfightposture.compat.humanoid;

import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.function.BiPredicate;

/** Side-safe bridge from the common lightweight patch to client posture render state. */
public final class HumanoidRenderState {
    private static volatile BiPredicate<Integer, UUID> clientResolver = (entityId, uuid) -> false;

    public static boolean shouldOverride(LivingEntity entity) {
        return entity != null
                && entity.level().isClientSide()
                && clientResolver.test(entity.getId(), entity.getUUID());
    }

    public static void installClientResolver(BiPredicate<Integer, UUID> resolver) {
        clientResolver = resolver == null ? (entityId, uuid) -> false : resolver;
    }

    private HumanoidRenderState() {
    }
}
