package dev.nire9734.epicfightposture.neoforge.client;

import dev.nire9734.epicfightposture.neoforge.EpicFightPostureNeoForge;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureClientConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/** Matches the Forge 3.0.2 client-only Combat Evolution execution sound scaling. */
@EventBusSubscriber(modid = EpicFightPostureNeoForge.MODID, value = Dist.CLIENT)
public final class NeoForgeClientSoundEvents {
    private static final ResourceLocation EXECUTION_1 =
            ResourceLocation.fromNamespaceAndPath("combat_evolution", "skill.execution1");
    private static final ResourceLocation EXECUTION_2 =
            ResourceLocation.fromNamespaceAndPath("combat_evolution", "skill.execution2");

    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        adjustVolume(event);
    }

    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        adjustVolume(event);
    }

    private static void adjustVolume(PlayLevelSoundEvent event) {
        // An integrated server is on the physical CLIENT, but its level is server-side.
        // Adjust only playback on the actual client level, once per sound event.
        if (!event.getLevel().isClientSide()) return;

        Holder<SoundEvent> sound = event.getSound();
        if (sound == null) return;
        ResourceLocation id = BuiltInRegistries.SOUND_EVENT.getKey(sound.value());
        if (!EXECUTION_1.equals(id) && !EXECUTION_2.equals(id)) return;

        float multiplier = NeoForgePostureClientConfig.executionSoundVolumeMultiplier();
        event.setNewVolume(Math.max(0.0F, event.getNewVolume() * multiplier));
    }

    private NeoForgeClientSoundEvents() {}
}
