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
            if (Float.isNaN(shown) || target < shown && target <= 0.0f) {
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
