package dev.nire9734.epicfightposture.animation;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.shelmarow.combat_evolution.gameassets.animation.ExecutionHitAnimation;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.gameasset.Armatures;

/** Registers EFPI-owned target-side animations used by compatibility patches. */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class PostureAnimations {
    public static AnimationManager.AnimationAccessor<ExecutionHitAnimation> SPIDER_EXECUTED;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerAnimations(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(EpicFightPostureIntegration.MODID, PostureAnimations::build);
    }

    private static void build(AnimationManager.AnimationBuilder builder) {
        SPIDER_EXECUTED = builder.nextAccessor(
                "spider/skill/execution/executed",
                accessor -> new ProceduralSpiderExecutionAnimation(0.08F, accessor, Armatures.SPIDER)
        );
    }

    private PostureAnimations() {
    }
}
