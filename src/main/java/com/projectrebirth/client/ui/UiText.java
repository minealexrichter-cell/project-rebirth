package com.projectrebirth.client.ui;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text that fits its widget (docs/UI_STYLE.md): a line that is too long is first drawn a little smaller (down to
 * {@link #MIN_SCALE}), and if it still does not fit it is cut with "…"; the window then shows the whole text in a tooltip
 * (see {@link Hints}). Russian text is longer than English, so every line of a window goes through here.
 */
public final class UiText {
    /** Smaller than this the game font gets blurry at GUI scale 2. */
    public static final float MIN_SCALE = 0.8f;

    private UiText() {
    }

    /** The text in capitals, as section labels and titles are written. */
    public static Component upper(Component text) {
        return Component.literal(text.getString().toUpperCase(Locale.ROOT));
    }

    /**
     * Draws one line within maxWidth, left-aligned, its top at y. Returns true if the line had to be cut (the caller
     * then offers the full text as a tooltip).
     */
    public static boolean line(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        Font font = Minecraft.getInstance().font;
        int width = font.width(text);
        if (width <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return false;
        }
        float scale = (float) maxWidth / width;
        if (scale >= MIN_SCALE) {
            scaled(graphics, text, x, y + (1.0f - scale) * 4.0f, scale, color);
            return false;
        }
        String cut = font.plainSubstrByWidth(text.getString(), (int) ((maxWidth - font.width("…") * MIN_SCALE) / MIN_SCALE)) + "…";
        scaled(graphics, Component.literal(cut).withStyle(text.getStyle()), x, y + (1.0f - MIN_SCALE) * 4.0f, MIN_SCALE, color);
        return true;
    }

    /** One line centred on centreX (fitted like {@link #line}). */
    public static boolean centred(GuiGraphics graphics, Component text, int centreX, int y, int maxWidth, int color) {
        Font font = Minecraft.getInstance().font;
        int width = Math.min(maxWidth, Math.round(font.width(text) * Math.max(MIN_SCALE, Math.min(1.0f, (float) maxWidth / Math.max(1, font.width(text))))));
        return line(graphics, text, centreX - width / 2, y, maxWidth, color);
    }

    /** Text drawn at a scale (1 is normal, 2 is the big digits of a display). */
    public static void scaled(GuiGraphics graphics, Component text, float x, float y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /**
     * Wrapped text in a box: as many lines as fit in maxHeight (9 px each); the last one ends with "…" if text is left
     * over. Returns true if the text was cut.
     */
    public static boolean wrapped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int maxHeight, int color) {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        int fit = Math.max(1, maxHeight / 9);
        for (int i = 0; i < Math.min(fit, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 9, color, false);
        }
        return lines.size() > fit;
    }

    /** Collects the cut texts of one frame with their places, to show the whole text under the mouse. */
    public static final class Hints {
        private record Hint(int x, int y, int width, int height, Component text) {
        }

        private final java.util.ArrayList<Hint> hints = new java.util.ArrayList<>();

        public void clear() {
            hints.clear();
        }

        /** Draws a fitted line and remembers it if it was cut. */
        public void line(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
            if (UiText.line(graphics, text, x, y, maxWidth, color)) {
                hints.add(new Hint(x, y, maxWidth, 9, text));
            }
        }

        public void add(int x, int y, int width, int height, Component text) {
            hints.add(new Hint(x, y, width, height, text));
        }

        /** The whole text of a cut line under the mouse, or null. */
        public Component at(double mouseX, double mouseY) {
            for (Hint hint : hints) {
                if (mouseX >= hint.x && mouseX < hint.x + hint.width && mouseY >= hint.y && mouseY < hint.y + hint.height) {
                    return hint.text;
                }
            }
            return null;
        }
    }
}
