package com.projectrebirth.client.screen;

import java.util.function.DoubleConsumer;

import javax.annotation.Nullable;

import com.projectrebirth.client.HudLayout;
import com.projectrebirth.config.RebirthClientConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Client settings: HUD on/off, size and position, and the transfer animation. Changes apply at once and are saved on closing. Frames show where the
 * panel and the bar are, since the real HUD is visible only in a body or an armor.
 */
public class HudSettingsScreen extends Screen {
    private static final int COLUMN_WIDTH = 200;
    private static final int ROW = 22;
    private static final int ROWS_ONE_COLUMN = 13;
    private static final int ROWS_TWO_COLUMNS = 9;
    /** The vanilla hearts start this far above the bottom of the screen and 91 left of its centre. */
    private static final int HEARTS_BOTTOM = 30;
    private static final int HEARTS_LEFT = 91;
    private static final int FRAME_COLOR = 0xFF7FE8FF;

    @Nullable
    private final Screen parent;

    public HudSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.projectrebirth.hud_settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 1.7.0, block I: the settings of the machines go in a second column when the screen is wide enough
        boolean two = twoColumns();
        int x = two ? width / 2 - COLUMN_WIDTH - 4 : (width - COLUMN_WIDTH) / 2;
        int y = Math.max(4, (height - rows() * ROW) / 2);
        int x2 = two ? width / 2 + 4 : x;
        int y2 = two ? y : y + 8 * ROW;
        int last = two ? 8 : 12;
        addRenderableWidget(Button.builder(enabledMessage(), button -> {
            RebirthClientConfig.HUD_ENABLED.set(!HudLayout.enabled());
            button.setMessage(enabledMessage());
        }).bounds(x, y, COLUMN_WIDTH, 20).build());
        addRenderableWidget(new Slider(x, y + ROW, "scale", 0.5, 3.0, 0.25, RebirthClientConfig.HUD_SCALE.get(),
                RebirthClientConfig.HUD_SCALE::set));
        addRenderableWidget(new Slider(x, y + 2 * ROW, "panel_x", 0, width, 1, RebirthClientConfig.HUD_PANEL_X.get(),
                value -> RebirthClientConfig.HUD_PANEL_X.set((int) value)));
        addRenderableWidget(new Slider(x, y + 3 * ROW, "panel_y", 0, height, 1, RebirthClientConfig.HUD_PANEL_Y.get(),
                value -> RebirthClientConfig.HUD_PANEL_Y.set((int) value)));
        addRenderableWidget(new Slider(x, y + 4 * ROW, "bar_x", -width / 2, width / 2, 1, RebirthClientConfig.HUD_BAR_OFFSET_X.get(),
                value -> RebirthClientConfig.HUD_BAR_OFFSET_X.set((int) value)));
        addRenderableWidget(new Slider(x, y + 5 * ROW, "bar_y", -height, HEARTS_BOTTOM, 1, RebirthClientConfig.HUD_BAR_OFFSET_Y.get(),
                value -> RebirthClientConfig.HUD_BAR_OFFSET_Y.set((int) value)));
        addRenderableWidget(Button.builder(animationMessage(), button -> {
            RebirthClientConfig.TRANSFER_ANIMATION_ENABLED.set(!RebirthClientConfig.TRANSFER_ANIMATION_ENABLED.get());
            button.setMessage(animationMessage());
        }).bounds(x, y + 6 * ROW, COLUMN_WIDTH, 20).build());
        addRenderableWidget(new Slider(x, y + 7 * ROW, "animation_seconds", 1.0, 10.0, 0.5, RebirthClientConfig.TRANSFER_ANIMATION_SECONDS.get(),
                RebirthClientConfig.TRANSFER_ANIMATION_SECONDS::set));
        // 1.7.0, block I: the machines' bars, the console's alarm mark, the tablet's lines
        toggle(x2, y2, "machine_bars", RebirthClientConfig.HUD_MACHINE_BARS);
        addRenderableWidget(new Slider(x2, y2 + ROW, "machine_bar_distance", 2, 64, 1, RebirthClientConfig.HUD_MACHINE_BAR_DISTANCE.get(),
                value -> RebirthClientConfig.HUD_MACHINE_BAR_DISTANCE.set((int) value)));
        // 1.7.1: one row each, so that the Russian words fit
        toggle(x2, y2 + 2 * ROW, "console_alarm", RebirthClientConfig.HUD_CONSOLE_ALARM);
        toggle(x2, y2 + 3 * ROW, "tablet_lines", RebirthClientConfig.HUD_TABLET_LINES);
        int half = (COLUMN_WIDTH - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.projectrebirth.hud_settings.reset"), button -> reset())
                .bounds(x, y + last * ROW, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(x + half + 4, y + last * ROW, half, 20).build());
    }

    private boolean twoColumns() {
        return width >= 2 * COLUMN_WIDTH + 16;
    }

    private int rows() {
        return twoColumns() ? ROWS_TWO_COLUMNS : ROWS_ONE_COLUMN;
    }

    private void toggle(int x, int y, String key, net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value) {
        toggleHalf(x, y, COLUMN_WIDTH, key, value);
    }

    private void toggleHalf(int x, int y, int w, String key, net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value) {
        addRenderableWidget(Button.builder(toggleMessage(key, value), button -> {
            value.set(!value.get());
            button.setMessage(toggleMessage(key, value));
        }).bounds(x, y, w, 20).build());
    }

    private static Component toggleMessage(String key, net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value) {
        return Component.translatable("screen.projectrebirth.hud_settings." + key, value.get() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private static Component enabledMessage() {
        return Component.translatable("screen.projectrebirth.hud_settings.enabled",
                HudLayout.enabled() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private static Component animationMessage() {
        return Component.translatable("screen.projectrebirth.hud_settings.animation",
                RebirthClientConfig.TRANSFER_ANIMATION_ENABLED.get() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private void reset() {
        RebirthClientConfig.HUD_ENABLED.set(RebirthClientConfig.HUD_ENABLED.getDefault());
        RebirthClientConfig.HUD_SCALE.set(RebirthClientConfig.HUD_SCALE.getDefault());
        RebirthClientConfig.HUD_PANEL_X.set(RebirthClientConfig.HUD_PANEL_X.getDefault());
        RebirthClientConfig.HUD_PANEL_Y.set(RebirthClientConfig.HUD_PANEL_Y.getDefault());
        RebirthClientConfig.HUD_BAR_OFFSET_X.set(RebirthClientConfig.HUD_BAR_OFFSET_X.getDefault());
        RebirthClientConfig.HUD_BAR_OFFSET_Y.set(RebirthClientConfig.HUD_BAR_OFFSET_Y.getDefault());
        RebirthClientConfig.TRANSFER_ANIMATION_ENABLED.set(RebirthClientConfig.TRANSFER_ANIMATION_ENABLED.getDefault());
        RebirthClientConfig.TRANSFER_ANIMATION_SECONDS.set(RebirthClientConfig.TRANSFER_ANIMATION_SECONDS.getDefault());
        RebirthClientConfig.HUD_MACHINE_BARS.set(RebirthClientConfig.HUD_MACHINE_BARS.getDefault());
        RebirthClientConfig.HUD_MACHINE_BAR_DISTANCE.set(RebirthClientConfig.HUD_MACHINE_BAR_DISTANCE.getDefault());
        RebirthClientConfig.HUD_CONSOLE_ALARM.set(RebirthClientConfig.HUD_CONSOLE_ALARM.getDefault());
        RebirthClientConfig.HUD_TABLET_LINES.set(RebirthClientConfig.HUD_TABLET_LINES.getDefault());
        rebuildWidgets();
    }

    /** In the world the real HUD stays visible behind the settings. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft == null || minecraft.level == null) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleY = Math.max(4, (height - rows() * ROW) / 2) - 12;
        graphics.drawCenteredString(font, title, width / 2, Math.max(0, titleY), 0xFFFFFF);
        if (HudLayout.enabled()) {
            float scale = HudLayout.scale();
            int panelX = HudLayout.panelX(width);
            int panelY = HudLayout.panelY(height);
            frame(graphics, panelX, panelY, Math.round(HudLayout.PANEL_WIDTH * scale), Math.round(HudLayout.PANEL_HEIGHT * scale),
                    Component.translatable("screen.projectrebirth.hud_settings.panel"));
            // the body's whole stack: integrity, the thin bars of wear, charge and heat, the thrust, the marks and alarms
            int barHeight = Math.round(HudLayout.BODY_STACK_HEIGHT * scale);
            frame(graphics, HudLayout.barX(width / 2 - HEARTS_LEFT, width), HudLayout.barBottom(height - HEARTS_BOTTOM, height) - barHeight,
                    Math.round(HudLayout.BODY_STACK_WIDTH * scale), barHeight, Component.translatable("screen.projectrebirth.hud_settings.bar"));
        }
    }

    private void frame(GuiGraphics graphics, int x, int y, int w, int h, Component label) {
        graphics.renderOutline(x - 1, y - 1, w + 2, h + 2, FRAME_COLOR);
        // Beside the frame when it fits there (the bar's place is right under the buttons), otherwise below it.
        boolean beside = x + w + 4 + font.width(label) <= width;
        graphics.drawString(font, label, beside ? x + w + 4 : x, beside ? y : y + h + 2, FRAME_COLOR);
    }

    @Override
    public void onClose() {
        RebirthClientConfig.CLIENT_SPEC.save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class Slider extends AbstractSliderButton {
        private final String key;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleConsumer target;

        Slider(int x, int y, String key, double min, double max, double step, double current, DoubleConsumer target) {
            super(x, y, COLUMN_WIDTH, 20, CommonComponents.EMPTY, max > min ? Mth.clamp((current - min) / (max - min), 0.0, 1.0) : 0.0);
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.target = target;
            updateMessage();
        }

        private double stepped() {
            return Math.round((min + value * (max - min)) / step) * step;
        }

        @Override
        protected void updateMessage() {
            double shown = stepped();
            String text = step < 1 ? String.format(java.util.Locale.ROOT, "%.2f", shown) : Integer.toString((int) shown);
            setMessage(Component.translatable("screen.projectrebirth.hud_settings." + key, text));
        }

        @Override
        protected void applyValue() {
            target.accept(stepped());
        }
    }
}
