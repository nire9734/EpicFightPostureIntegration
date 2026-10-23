package dev.nire9734.epicfightposture.neoforge.network;

import java.util.Map;
import java.util.WeakHashMap;
import dev.nire9734.epicfightposture.NeoForgePostureServerEvents;
import dev.nire9734.epicfightposture.client.ClientPostureStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sync only when posture actually changes, with a four-tick periodic fallback. */
public final class NeoForgePostureSync {
    private record Snapshot(float current, float max, boolean broken) {
        boolean same(Snapshot other) {
            return other != null
                    && Float.floatToIntBits(current) == Float.floatToIntBits(other.current)
                    && Float.floatToIntBits(max) == Float.floatToIntBits(other.max)
                    && broken == other.broken;
        }
    }

    private static final Map<LivingEntity, Snapshot> LAST = new WeakHashMap<>();
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(NeoForgePostureSync::onStartTracking);
        NeoForge.EVENT_BUS.addListener(NeoForgePostureSync::onLeave);
    }

    public static void syncPeriodic(LivingEntity mob, NeoForgePostureServerEvents.SyncState state) {
        if (Math.floorMod(mob.tickCount + mob.getId(), 4) == 0) syncNow(mob, state);
    }

    public static void syncNow(LivingEntity mob, NeoForgePostureServerEvents.SyncState state) {
        if (mob.level().isClientSide()) return;
        Snapshot next = new Snapshot(state.current(), state.max(), state.broken());
        if (next.same(LAST.get(mob))) return;
        LAST.put(mob, next);
        PacketDistributor.sendToPlayersTrackingEntity(mob, packet(mob, next, true));
    }

    public static void removeNow(LivingEntity mob) {
        Snapshot old = LAST.remove(mob);
        if (old != null && !mob.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntity(mob, packet(mob, old, false));
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player) ||
                !(event.getTarget() instanceof LivingEntity mob)) return;
        NeoForgePostureServerEvents.SyncState state = NeoForgePostureServerEvents.stateForTracking(mob);
        if (state != null) {
            Snapshot snapshot = new Snapshot(state.current(), state.max(), state.broken());
            PacketDistributor.sendToPlayer(player, packet(mob, snapshot, true));
        }
    }

    private static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        if (event.getLevel().isClientSide()) {
            ClientPostureStore.remove(mob.getId(), mob.getUUID());
        } else {
            LAST.remove(mob);
            NeoForgePostureServerEvents.forget(mob);
        }
    }

    private static PostureStatePayload packet(LivingEntity mob, Snapshot value, boolean present) {
        return new PostureStatePayload(mob.getId(), mob.getUUID(),
                value.current(), value.max(), value.broken(), present);
    }

    private NeoForgePostureSync() {}
}
