package dev.nire9734.epicfightposture.neoforge.animation;

import dev.nire9734.epicfightposture.animation.ProceduralSpiderExecutionAnimation;
import dev.nire9734.epicfightposture.neoforge.EpicFightPostureNeoForge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.shelmarow.combat_evolution.gameassets.animation.ExecutionHitAnimation;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.gameasset.Armatures;

/** Reuses Forge's exact authored spider victim animation, not a ported copy. */
@EventBusSubscriber(modid = EpicFightPostureNeoForge.MODID,
        bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgePostureAnimations {
    public static AnimationManager.AnimationAccessor<ExecutionHitAnimation> SPIDER_EXECUTED;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerAnimations(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(EpicFightPostureNeoForge.MODID, NeoForgePostureAnimations::build);
    }

    private static void build(AnimationManager.AnimationBuilder builder) {
        SPIDER_EXECUTED = builder.nextAccessor(
                "spider/skill/execution/executed",
                accessor -> new ProceduralSpiderExecutionAnimation(0.08F, accessor, Armatures.SPIDER));
    }

    private NeoForgePostureAnimations() {}
}
