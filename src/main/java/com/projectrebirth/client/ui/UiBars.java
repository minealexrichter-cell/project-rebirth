package com.projectrebirth.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Gauges and bars with notches; {@link Smooth} makes a shown value follow the real one over a quarter of a second. */
public final class UiBars {
    private UiBars() {
    }

    /** A vertical gauge on a screen, filling from the bottom, with a dark notch every 4 px (energy, charge). */
    public static void vertical(GuiGraphics graphics, int x, int y, int width, int height, float fraction, int color, int notch) {
        UiPanels.screen(graphics, x, y, width, height);
        int inner = height - 4;
        int filled = Math.round(inner * Mth.clamp(fraction, 0.0f, 1.0f));
        int bottom = y + height - 2;
        graphics.fill(x + 2, bottom - filled, x + width - 2, bottom, color);
        for (int line = bottom - 4; line > bottom - filled; line -= 4) {
            graphics.fill(x + 2, line, x + width - 2, line + 1, notch);
        }
    }

    /** A horizontal gauge on a screen, filling from the left, with a dark notch every 4 px (charge, heat, wear). */
    public static void horizontal(GuiGraphics graphics, int x, int y, int width, int height, float fraction, int color) {
        UiPanels.screen(graphics, x, y, width, height);
        int inner = width - 4;
        int filled = Math.round(inner * Mth.clamp(fraction, 0.0f, 1.0f));
        // 1.8.0, block K: the fill and its notches in one draw call (a console of cards drew hundreds of them a frame)
        try (UiQuads quads = UiQuads.begin(graphics)) {
            quads.fill(x + 2, y + 2, x + 2 + filled, y + height - 2, color);
            int notch = shade(color);
            for (int line = x + 5; line < x + 2 + filled; line += 4) {
                quads.fill(line, y + 2, line + 1, y + height - 2, notch);
            }
        }
    }

    /**
     * A meter: an icon, a horizontal gauge and the value as text right of it ("74 %"). The value always goes with the
     * colour, so the meaning does not rest on colour alone.
     */
    public static void meter(GuiGraphics graphics, net.minecraft.resources.ResourceLocation icon, int x, int y, int width,
                             float fraction, int color, net.minecraft.network.chat.Component value) {
        int barX = x;
        if (icon != null) {
            UiSprites.draw(graphics, icon, x, y + 1, 7, 7);
            barX += 10;
        }
        int textWidth = 30;
        horizontal(graphics, barX, y, width - (barX - x) - textWidth - 3, 9, fraction, color);
        UiText.line(graphics, value, x + width - textWidth, y + 1, textWidth, color);
    }

    /**
     * Integrity with two scales, as machines and power armor have it: the structural scale above, the critical one
     * below, each coloured by its level (green, amber at 50 %, red at 20 %).
     */
    public static void integrity(GuiGraphics graphics, int x, int y, int width, float structural, float critical) {
        horizontal(graphics, x, y, width, 6, structural, UiColors.integrity(structural));
        horizontal(graphics, x, y + 7, width, 6, critical, UiColors.integrity(critical));
    }

    private static int shade(int color) {
        int r = (color >> 16 & 255) * 3 / 5;
        int g = (color >> 8 & 255) * 3 / 5;
        int b = (color & 255) * 3 / 5;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A horizontal progress bar on an amber track (the process of a station). */
    public static void progress(GuiGraphics graphics, int x, int y, int width, int height, float fraction, int color) {
        UiSprites.draw(graphics, UiSprites.TRACK_AMBER, x, y, width, height);
        int filled = Math.round((width - 2) * Mth.clamp(fraction, 0.0f, 1.0f));
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, color);
        }
    }

    /** A shown value that moves smoothly to the real one (0.25 s), so bars do not jump. */
    public static final class Smooth {
        private float shown = Float.NaN;
        private long last;

        public float follow(float target) {
            long now = System.nanoTime();
            if (Float.isNaN(shown) || target < shown && target <= 0.0f || !UiSounds.animations()) {
                shown = target;
            } else {
                float seconds = Math.min(0.1f, (now - last) / 1.0e9f);
                shown += (target - shown) * Math.min(1.0f, seconds * 12.0f);
                if (Math.abs(target - shown) < 0.002f) {
                    shown = target;
                }
            }
            last = now;
            return shown;
        }
    }
}
