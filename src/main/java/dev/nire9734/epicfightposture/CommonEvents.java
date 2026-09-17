package dev.nire9734.epicfightposture;

import dev.nire9734.epicfightposture.sync.PostureSyncEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.shelmarow.combat_evolution.api.event.OnExecutionStartEvent;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener;
import yesman.epicfight.world.entity.eventlistener.TakeDamageEvent;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    private static final UUID PARRY_LISTENER_ID = UUID.fromString("b45b33bd-7c6f-4bf4-838e-5f247f095711");
    private static final Set<ServerPlayerPatch> REGISTERED_PATCHES =
            Collections.newSetFromMap(new WeakHashMap<>());

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Entity offender = GuardSkill.getOffender(event.getSource());
        if (!(offender instanceof Player player) || target == player) {
            return;
        }
        PostureManager.onPlayerDamage(target, player, event.getAmount(), event.getSource());
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof ServerPlayer serverPlayer) {
            registerParryListener(serverPlayer);
        }
        if (PostureManager.tick(entity)) {
            PostureSyncEvents.syncPeriodic(entity);
        }
    }

    @SubscribeEvent
    public static void onExecutionStart(OnExecutionStartEvent event) {
        Object original = event.getTarget().getOriginal();
        if (original instanceof LivingEntity target) {
            PostureManager.onExecutionStart(target);
        }
    }

    private static void registerParryListener(ServerPlayer player) {
        ServerPlayerPatch patch = EpicFightCapabilities.getServerPlayerPatch(player);
        if (patch == null || !REGISTERED_PATCHES.add(patch)) {
            return;
        }
        patch.getEventListener().addEventListener(
                PlayerEventListener.EventType.TAKE_DAMAGE_EVENT_ATTACK,
                PARRY_LISTENER_ID,
                CommonEvents::afterTakeDamageAttack,
                -1
        );
    }

    private static void afterTakeDamageAttack(TakeDamageEvent.Attack event) {
        if (!event.isParried()) {
            return;
        }
        Entity offender = GuardSkill.getOffender(event.getDamageSource());
        if (offender instanceof LivingEntity livingEntity) {
            PostureManager.onParried(livingEntity, event.getPlayerPatch());
        }
    }

    private CommonEvents() {
    }
}
