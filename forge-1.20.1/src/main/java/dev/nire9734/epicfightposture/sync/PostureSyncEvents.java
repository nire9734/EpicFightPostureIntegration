package dev.nire9734.epicfightposture.sync;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.PostureManager;
import dev.nire9734.epicfightposture.network.BridgeNetwork;
import dev.nire9734.epicfightposture.network.PostureSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/** Synchronizes server posture state to tracking clients. */
@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PostureSyncEvents {
    private static final int SYNC_INTERVAL_TICKS = 4;
    private static final Map<LivingEntity, Snapshot> LAST_SENT = new WeakHashMap<>();

    /** Called from the single living-tick handler; does nothing for entities without posture state. */
    public static void syncPeriodic(LivingEntity entity) {
        if (!RuntimeAccess.isServerEntity(entity)) {
            return;
        }
        if (Math.floorMod(entity.tickCount + entity.getId(), SYNC_INTERVAL_TICKS) != 0) {
            return;
        }

        Snapshot current = snapshot(entity, false);
        Snapshot previous = LAST_SENT.get(entity);
        if (current == null) {
            if (previous != null) {
                BridgeNetwork.sendTracking(entity, packet(entity, previous, false));
                LAST_SENT.remove(entity);
            }
            return;
        }

        if (!current.same(previous)) {
            LAST_SENT.put(entity, current);
            BridgeNetwork.sendTracking(entity, packet(entity, current, true));
        }
    }

    /** Sends an immediate state change caused by damage, parry, break, or execution transitions. */
    public static void syncNow(LivingEntity entity) {
        if (!RuntimeAccess.isServerEntity(entity)) {
            return;
        }
        Snapshot current = snapshot(entity, false);
        if (current == null) {
            return;
        }
        Snapshot previous = LAST_SENT.get(entity);
        if (!current.same(previous)) {
            LAST_SENT.put(entity, current);
            BridgeNetwork.sendTracking(entity, packet(entity, current, true));
        }
    }

    /** Removes a stale client HUD entry when a server-side state becomes ineligible. */
    public static void removeNow(LivingEntity entity) {
        if (!RuntimeAccess.isServerEntity(entity)) {
            return;
        }
        Snapshot previous = LAST_SENT.remove(entity);
        if (previous != null) {
            BridgeNetwork.sendTracking(entity, packet(entity, previous, false));
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity livingEntity) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Snapshot snapshot = snapshot(livingEntity, false);
        if (snapshot != null) {
            BridgeNetwork.sendPlayer(player, packet(livingEntity, snapshot, true));
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        LAST_SENT.remove(livingEntity);
        PostureManager.forgetState(livingEntity, false);
    }

    private static PostureSyncPacket packet(Entity entity, Snapshot snapshot, boolean present) {
        return new PostureSyncPacket(
                RuntimeAccess.entityId(entity),
                RuntimeAccess.entityUuid(entity),
                snapshot.current,
                snapshot.max,
                snapshot.broken,
                present
        );
    }

    private static Snapshot snapshot(LivingEntity entity, boolean forceCreate) {
        PostureManager.SyncState state = PostureManager.syncState(entity, forceCreate);
        return state == null ? null : new Snapshot(state.current(), state.max(), state.broken());
    }

    private PostureSyncEvents() {
    }

    private record Snapshot(float current, float max, boolean broken) {
        boolean same(Snapshot other) {
            return other != null
                    && Float.floatToIntBits(current) == Float.floatToIntBits(other.current)
                    && Float.floatToIntBits(max) == Float.floatToIntBits(other.max)
                    && broken == other.broken;
        }
    }
}
