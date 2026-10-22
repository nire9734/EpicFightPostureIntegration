package dev.nire9734.epicfightposture.client;

import dev.nire9734.epicfightposture.PostureClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Small in-game client config screen exposed from Forge's Mods menu. */
public final class PostureConfigScreen extends Screen {
    private static final int CONTROL_WIDTH = 220;

    private final Screen parent;
    private Button mobHudVisibilityButton;
    private ExecutionVolumeSlider executionVolumeSlider;

    public PostureConfigScreen(Screen parent) {
        super(Component.translatable("config.epicfight_posture_integration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - CONTROL_WIDTH / 2;
        int hudY = this.height / 2 - 48;
        int sliderY = this.height / 2 + 18;

        this.mobHudVisibilityButton = this.addRenderableWidget(
                Button.builder(hudVisibilityMessage(), button -> {
                            PostureHudPolicy.Visibility[] values = PostureHudPolicy.Visibility.values();
                            PostureHudPolicy.Visibility current = PostureClientConfig.mobHealthBarVisibility();
                            PostureClientConfig.setMobHealthBarVisibility(values[(current.ordinal() + 1) % values.length]);
                            button.setMessage(hudVisibilityMessage());
                        })
                        .bounds(left, hudY, CONTROL_WIDTH, 20)
                        .build()
        );

        this.executionVolumeSlider = this.addRenderableWidget(
                new ExecutionVolumeSlider(
                        left,
                        sliderY,
                        CONTROL_WIDTH,
                        20,
                        PostureClientConfig.executionSoundVolumeMultiplier()
                )
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable("config.epicfight_posture_integration.reset"),
                                button -> {
                                    this.executionVolumeSlider.setMultiplier(
                                            PostureClientConfig.DEFAULT_EXECUTION_SOUND_VOLUME_MULTIPLIER
                                    );
                                    PostureClientConfig.setMobHealthBarVisibility(
                                            PostureHudPolicy.Visibility.DAMAGED_ONLY);
                                    this.mobHudVisibilityButton.setMessage(hudVisibilityMessage());
                                }
                        )
                        .bounds(left, sliderY + 30, 106, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                        .bounds(left + 114, sliderY + 30, 106, 20)
                        .build()
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
        guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("config.epicfight_posture_integration.mob_health_bar_visibility.description"),
                this.width / 2,
                this.height / 2 - 65,
                0xA0A0A0
        );
        guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("config.epicfight_posture_integration.execution_sound_volume.description"),
                this.width / 2,
                this.height / 2 + 1,
                0xA0A0A0
        );
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        PostureClientConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private static Component hudVisibilityMessage() {
        PostureHudPolicy.Visibility visibility = PostureClientConfig.mobHealthBarVisibility();
        return Component.translatable(
                "config.epicfight_posture_integration.mob_health_bar_visibility",
                Component.translatable("config.epicfight_posture_integration.mob_health_bar_visibility."
                        + visibility.name().toLowerCase(java.util.Locale.ROOT))
        );
    }

    private static final class ExecutionVolumeSlider extends AbstractSliderButton {
        private ExecutionVolumeSlider(int x, int y, int width, int height, double multiplier) {
            super(x, y, width, height, Component.empty(), clamp(multiplier));
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = (int) Math.round(this.value * 100.0D);
            this.setMessage(Component.translatable(
                    "config.epicfight_posture_integration.execution_sound_volume",
                    percent
            ));
        }

        @Override
        protected void applyValue() {
            double rounded = Math.round(this.value * 100.0D) / 100.0D;
            this.value = rounded;
            PostureClientConfig.setExecutionSoundVolumeMultiplier(rounded);
            this.updateMessage();
        }

        private void setMultiplier(double multiplier) {
            this.value = clamp(multiplier);
            this.applyValue();
        }

        private static double clamp(double value) {
            return Math.max(0.0D, Math.min(1.0D, value));
        }
    }
}
