package dev.nire9734.epicfightposture.neoforge.compat;

import dev.nire9734.epicfightposture.SpecialRenderFailures;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-installed renderer activation bridge. Contains no Minecraft client
 * references and is also safe to load on a dedicated server.
 */
public final class NeoForgeHumanoidRenderGate {
    private static final Set<EntityType<?>> VERIFIED = ConcurrentHashMap.newKeySet();
    private static volatile BiPredicate<Integer, UUID> serverStateResolver = (id, uuid) -> false;

    public static void installClientStateResolver(BiPredicate<Integer, UUID> resolver) {
        serverStateResolver = resolver != null ? resolver : (id, uuid) -> false;
    }

    public static void startRendererReload() {
        VERIFIED.clear();
        SpecialRenderFailures.clear();
    }

    public static void verified(EntityType<?> type) {
        if (type != null) VERIFIED.add(type);
    }

    public static boolean shouldOverride(LivingEntity mob) {
        return mob != null && !SpecialRenderFailures.isDisabled(mob.getType())
                && NeoForgeSpecialRenderPolicy.shouldOverride(
                mob.level().isClientSide(),
                NeoForgeAutoCompatibility.isEnabled(mob.getType()),
                VERIFIED.contains(mob.getType()),
                serverStateResolver.test(mob.getId(), mob.getUUID())
        );
    }

    private NeoForgeHumanoidRenderGate() {}
}
