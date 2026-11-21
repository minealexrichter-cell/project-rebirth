package com.projectrebirth.client.hud;

import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Small drawing parts of the HUD in the style "Industrial terminal" (1.8.0 block C): the translucent HUD panel with an
 * amber edge (docs/UI_STYLE.md: rgba(14,19,17,.72)), a key cap "[N]", text bars of the robots' displays "[####....]",
 * thin bars of the premium visor, and the time of a frame for the animations.
 */
public final class HudDraw {
    public static final int PANEL = 0xB80E1311;
    public static final int PANEL_EDGE = 0x8CFFB648;
    /** The robots' monochrome display (T1–T2). */
    public static final int ROBOT_GREEN = 0xFF5CFF7A;
    public static final int ROBOT_DIM = 0xFF2F8C43;
    public static final int ROBOT_BACK = 0xC0041006;
    /** The premium visor (T5): white with a soft shadow. */
    public static final int CLEAN = 0xFFFFFFFF;
    public static final int CLEAN_TRACK = 0x40FFFFFF;

    private HudDraw() {
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** A translucent HUD panel with a 1 px amber edge. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 1, PANEL_EDGE);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_EDGE);
        graphics.fill(x, y + 1, x + 1, y + height - 1, PANEL_EDGE);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, PANEL_EDGE);
    }

    /** A key cap with the key's name, amber: returns its width. */
    public static int key(GuiGraphics graphics, Component key, int x, int y) {
        Font font = font();
        int width = font.width(key) + 4;
        graphics.fill(x, y - 1, x + width, y, UiColors.AMBER);
        graphics.fill(x, y + 8, x + width, y + 9, UiColors.AMBER);
        graphics.fill(x, y - 1, x + 1, y + 9, UiColors.AMBER);
        graphics.fill(x + width - 1, y - 1, x + width, y + 9, UiColors.AMBER);
        graphics.drawString(font, key, x + 2, y, UiColors.AMBER, false);
        return width + 3;
    }

    /** "[######....]": a bar of a rough display, cells of the given count. */
    public static String textBar(float fraction, int cells) {
        int full = Math.round(Mth.clamp(fraction, 0.0f, 1.0f) * cells);
        return "[" + "#".repeat(full) + ".".repeat(cells - full) + "]";
    }

    /** A 2 px bar of the premium visor on a faint track. */
    public static void thinBar(GuiGraphics graphics, int x, int y, int width, float fraction, int color) {
        graphics.fill(x, y, x + width, y + 2, CLEAN_TRACK);
        graphics.fill(x, y, x + Math.round(width * Mth.clamp(fraction, 0.0f, 1.0f)), y + 2, color);
    }

    /** Milliseconds for the animations; frozen at 0 with the animations off. */
    public static long time() {
        return UiSounds.animations() ? System.currentTimeMillis() : 0L;
    }

    /** Whether a blinking thing is lit now (always lit with "less motion"). */
    public static boolean blinkOn(int periodMs) {
        return UiSounds.reducedMotion() || (System.currentTimeMillis() / periodMs) % 2 == 0;
    }

    /** "3:05" from seconds. */
    public static String clock(int seconds) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
