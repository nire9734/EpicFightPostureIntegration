package dev.nire9734.epicfightposture.client;

import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.PostureServerConfig;
import dev.nire9734.epicfightposture.compat.humanoid.VanillaHumanoidCompat;
import dev.nire9734.epicfightposture.network.ServerVersionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Event-driven cleanup for client posture and synchronized server-policy caches. */
@Mod.EventBusSubscriber(
        modid = EpicFightPostureIntegration.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class ClientPostureEvents {
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        ClientPostureStore.remove(entity.getId(), entity.getUUID());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientPostureStore.clear();
        }
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        VanillaHumanoidCompat.recoverClientRegistrationsOnWorldJoin();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPostureStore.clear();
        PostureServerConfig.clearRemoteSnapshot();
        ServerVersionNotice.clear();
    }

    /** Shows the one-line version notice once the chat is available after login. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ServerVersionNotice.hasPending()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        String local = ServerVersionPacket.localModVersion();
        String server = ServerVersionNotice.takeMismatch(local);
        if (server != null) {
            minecraft.gui.getChat().addMessage(Component.translatable(
                    ServerVersionNotice.TRANSLATION_KEY, server, local).withStyle(ChatFormatting.YELLOW));
        }
    }

    private ClientPostureEvents() {
    }
}
