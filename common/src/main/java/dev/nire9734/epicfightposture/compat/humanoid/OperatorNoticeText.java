package dev.nire9734.epicfightposture.compat.humanoid;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Builds translatable chat components for operator notices. The server sends
 * translation keys, so each operator's client renders them in its own language.
 * Uses only Component APIs shared by Minecraft 1.20.1 and 1.21.1.
 */
public final class OperatorNoticeText {
    public static Component of(RendererDiscoveryShared.OperatorNotice notice) {
        MutableComponent details = Component.empty();
        boolean first = true;
        for (RendererDiscoveryShared.NoticeDetail detail : notice.details()) {
            if (!first) details.append(Component.translatable(RendererDiscoveryShared.SEPARATOR_KEY));
            details.append(Component.translatable(detail.translationKey(), detail.count()));
            first = false;
        }
        MutableComponent message = Component.translatable(notice.headlineKey(), details);
        return notice.problems() ? message.withStyle(ChatFormatting.YELLOW) : message;
    }

    public static Component failure() {
        return Component.translatable(RendererDiscoveryShared.FAILED_KEY).withStyle(ChatFormatting.YELLOW);
    }

    private OperatorNoticeText() {
    }
}
