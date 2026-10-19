package dev.nire9734.epicfightposture;

import dev.nire9734.epicfightposture.sync.PostureSyncEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.shelmarow.combat_evolution.api.event.OnExecutionStartEvent;
import yesman.epicfight.skill.guard.GuardSkill;

@Mod.EventBusSubscriber(modid = EpicFightPostureIntegration.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    // Forge 1.20.1 ends at LOWEST; MONITOR does not exist.
    // Record the attack, but wait until HP actually changes before giving posture:
    // same-priority KubeJS damage caps can still run after this callback.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        Entity offender = GuardSkill.getOffender(event.getSource());
        boolean playerHit = offender instanceof Player && target != offender;
        PostureManager.observeDamageEvent(
                target, event.getAmount(), event.getSource(), playerHit && !event.isCanceled());
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
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

    private CommonEvents() {
    }
}
