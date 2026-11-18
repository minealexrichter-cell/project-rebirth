package com.projectrebirth.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A tooltip of the style: a dark screen with an amber edge, a title in amber, then lines "label … value unit" with the
 * values aligned on the right, and plain lines of text. It stays inside the screen.
 */
public final class UiTooltip {
    private static final int MAX_TEXT = 200;

    private record Line(Component label, Component value, int color, ResourceLocation icon) {
    }

    private Component title;
    private final List<Line> lines = new ArrayList<>();

    public static UiTooltip of(Component title) {
        UiTooltip tooltip = new UiTooltip();
        tooltip.title = title;
        return tooltip;
    }

    /** A line "label … value" (the value in its colour on the right; the unit belongs in the value: "84 %", "32 FE/t"). */
    public UiTooltip row(Component label, Component value, int color) {
        lines.add(new Line(label, value, color, null));
        return this;
    }

    public UiTooltip row(ResourceLocation icon, Component label, Component value, int color) {
        lines.add(new Line(label, value, color, icon));
        return this;
    }

    /** A plain line of text, wrapped if it is long. */
    public UiTooltip text(Component text, int color) {
        Font font = Minecraft.getInstance().font;
        for (var part : font.getSplitter().splitLines(text, MAX_TEXT, text.getStyle())) {
            lines.add(new Line(Component.literal(part.getString()).withStyle(text.getStyle()), null, color, null));
        }
        return this;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int width = title == null ? 0 : font.width(title);
        for (Line line : lines) {
            int w = (line.icon != null ? 10 : 0) + font.width(line.label) + (line.value != null ? 12 + font.width(line.value) : 0);
            width = Math.max(width, w);
        }
        width = Math.min(width, MAX_TEXT + 20) + 12;
        int height = (title == null ? 0 : 13) + lines.size() * 10 + 8;
        int x = mouseX + 12;
        int y = mouseY - 12;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        if (x + width > screenW - 2) {
            x = Math.max(2, mouseX - 12 - width);
        }
        y = Math.max(2, Math.min(y, screenH - height - 2));
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 400.0f);
        UiSprites.draw(graphics, UiSprites.TOOLTIP, x, y, width, height);
        int lineY = y + 5;
        if (title != null) {
            UiText.line(graphics, title, x + 6, lineY, width - 12, UiColors.AMBER);
            graphics.fill(x + 4, lineY + 10, x + width - 4, lineY + 11, 0xFF2A332E);
            lineY += 13;
        }
        for (Line line : lines) {
            int textX = x + 6;
            if (line.icon != null) {
                UiSprites.draw(graphics, line.icon, textX, lineY, 7, 7);
                textX += 10;
            }
            if (line.value == null) {
                UiText.line(graphics, line.label, textX, lineY, x + width - 6 - textX, line.color);
            } else {
                int valueWidth = font.width(line.value);
                UiText.line(graphics, line.label, textX, lineY, x + width - 18 - valueWidth - textX, UiColors.TEXT_SECONDARY);
                graphics.drawString(font, line.value, x + width - 6 - valueWidth, lineY, line.color, false);
            }
            lineY += 10;
        }
        graphics.pose().popPose();
    }
}
