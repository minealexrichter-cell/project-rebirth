package com.projectrebirth.client.hud;

import java.util.List;

import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Warnings of the visor and the helmet in words (1.8.0 block C), in the middle of the screen below the crosshair: at most
 * two, the most urgent first. Each look matches its HUD: the robots' inverted green line "!! LOW CHARGE — 3 MIN !!", the
 * terminal's plate with an icon, the premium visor's dark strip with an amber edge. The first one blinks (steady with
 * "less motion").
 */
public final class Warnings {
    public enum Look { ROBOT, TERMINAL, CLEAN }

    private static final int MAX = 2;

    private Warnings() {
    }

    public static void draw(GuiGraphics graphics, List<Component> warnings, Look look) {
        HudElement element = HudElement.WARNINGS;
        if (warnings.isEmpty() || !element.visible()) {
            return;
        }
        Font font = HudDraw.font();
        int[] at = element.position(graphics.guiWidth(), graphics.guiHeight());
        float scale = element.scale();
        int width = element.width;
        graphics.pose().pushPose();
        graphics.pose().translate(at[0], at[1], 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        for (int i = 0; i < Math.min(MAX, warnings.size()); i++) {
            Component text = warnings.get(i);
            int y = i * 15;
            switch (look) {
                case ROBOT -> {
                    Component line = Component.literal(i == 0 ? "!! " + text.getString().toUpperCase(java.util.Locale.ROOT) + " !!"
                            : "> " + text.getString().toUpperCase(java.util.Locale.ROOT));
                    int w = Math.min(width, font.width(line) + 8);
                    int x = (width - w) / 2;
                    if (i == 0 && HudDraw.blinkOn(500)) {
                        graphics.fill(x, y, x + w, y + 12, HudDraw.ROBOT_GREEN);
                        UiText.centred(graphics, line, width / 2, y + 2, width - 8, 0xFF041006);
                    } else {
                        UiText.centred(graphics, line, width / 2, y + 2, width - 8, HudDraw.ROBOT_GREEN);
                    }
                }
                case TERMINAL -> {
                    int w = Math.min(width, font.width(text) + 22);
                    int x = (width - w) / 2;
                    UiSprites.Tone tone = i == 0 ? UiSprites.Tone.ERROR : UiSprites.Tone.WARNING;
                    UiSprites.draw(graphics, tone.plate, x, y, w, 14);
                    if (i > 0 || HudDraw.blinkOn(600)) {
                        UiSprites.draw(graphics, tone.icon, x + 4, y + 4, 7, 7);
                    }
                    UiText.line(graphics, text, x + 15, y + 3, w - 19, tone.text);
                }
                case CLEAN -> {
                    int w = Math.min(width, font.width(text) + 14);
                    int x = (width - w) / 2;
                    graphics.fill(x, y, x + w, y + 13, 0x66000000);
                    graphics.fill(x, y, x + 2, y + 13, i == 0 ? UiColors.AMBER : UiColors.AMBER_DIM);
                    UiText.line(graphics, text, x + 7, y + 3, w - 10, 0xFFFFFFFF);
                }
            }
        }
        graphics.pose().popPose();
    }
}
