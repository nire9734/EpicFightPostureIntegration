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
    // Observe post-mitigation damage after LOWEST (e.g. Soulrend damage-cap)
    // handlers without modifying the event itself at MONITOR priority.
    @SubscribeEvent(priority = EventPriority.MONITOR)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F) {
            return;
        }
        LivingEntity target = event.getEntity();
        Entity offender = GuardSkill.getOffender(event.getSource());
        if (!(offender instanceof Player) || target == offender) {
            return;
        }
        PostureManager.onPlayerDamage(target, event.getAmount(), event.getSource());
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
