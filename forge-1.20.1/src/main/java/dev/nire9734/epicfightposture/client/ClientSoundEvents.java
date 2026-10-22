package dev.nire9734.epicfightposture.client;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.PostureClientConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Applies the local user's execution-volume preference without changing server behavior. */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class ClientSoundEvents {
    private static final ResourceLocation CE_EXECUTION_1 =
            ResourceLocation.fromNamespaceAndPath("combat_evolution", "skill.execution1");
    private static final ResourceLocation CE_EXECUTION_2 =
            ResourceLocation.fromNamespaceAndPath("combat_evolution", "skill.execution2");

    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        adjustExecutionVolume(event);
    }

    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        adjustExecutionVolume(event);
    }

    private static void adjustExecutionVolume(PlayLevelSoundEvent event) {
        // On an integrated server the physical side is CLIENT even while the server thread is running.
        // Only alter the actual client playback event so the multiplier is never applied twice.
        if (!event.getLevel().isClientSide()) {
            return;
        }

        Holder<SoundEvent> sound = event.getSound();
        if (sound == null) {
            return;
        }

        ResourceLocation soundId = ForgeRegistries.SOUND_EVENTS.getKey(sound.value());
        if (!CE_EXECUTION_1.equals(soundId) && !CE_EXECUTION_2.equals(soundId)) {
            return;
        }

        float multiplier = PostureClientConfig.executionSoundVolumeMultiplier();
        event.setNewVolume(Math.max(0.0F, event.getNewVolume() * multiplier));
    }

    private ClientSoundEvents() {
    }
}
