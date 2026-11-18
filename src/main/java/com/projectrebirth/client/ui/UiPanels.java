package com.projectrebirth.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Panels of a window: the metal body, the title bar, sunken screens, section labels, status plates and status badges.
 * Sizes are those of docs/UI_STYLE.md, in GUI pixels.
 */
public final class UiPanels {
    /** Height of the title bar. */
    public static final int TITLE_HEIGHT = 20;
    /** Inner margin of the window body. */
    public static final int PADDING = 7;
    /** Height of a status plate and a status badge. */
    public static final int PLATE_HEIGHT = 14;

    private UiPanels() {
    }

    /** The metal body of a window with its rivets. */
    public static void window(GuiGraphics graphics, int x, int y, int width, int height) {
        UiSprites.draw(graphics, UiSprites.WINDOW, x, y, width, height);
    }

    /**
     * The title bar: an icon (a 9 x 9 sprite, or none), the title in capitals, hazard stripes up to stripesEnd (where the
     * window's buttons begin), and a line under it. Returns true if the title had to be cut.
     */
    public static boolean title(GuiGraphics graphics, int x, int y, int width, Component title, ResourceLocation icon, int stripesEnd) {
        int textX = x + 9;
        if (icon != null) {
            UiSprites.draw(graphics, icon, x + 9, y + 6, 9, 9);
            textX = x + 23;
        }
        Component upper = UiText.upper(title);
        int textMax = Math.max(20, stripesEnd - textX - 30);
        int textWidth = Math.min(textMax, Minecraft.getInstance().font.width(upper));
        boolean cut = UiText.line(graphics, upper, textX, y + 7, textMax, UiColors.TEXT);
        int stripes = textX + textWidth + 6;
        if (stripesEnd - stripes >= 8) {
            UiSprites.draw(graphics, UiSprites.HAZARD, stripes, y + 8, stripesEnd - stripes, 5);
        }
        UiSprites.draw(graphics, UiSprites.DIVIDER, x + 2, y + TITLE_HEIGHT - 1, width - 4, 2);
        return cut;
    }

    /** A raised panel inside a window (a group of controls). */
    public static void inner(GuiGraphics graphics, int x, int y, int width, int height) {
        UiSprites.draw(graphics, UiSprites.PANEL, x, y, width, height);
    }

    /** A horizontal line between parts of a window. */
    public static void divider(GuiGraphics graphics, int x, int y, int width) {
        UiSprites.draw(graphics, UiSprites.DIVIDER, x, y, width, 2);
    }

    /** A card of a list (a body, a machine, an alarm); the chosen one is framed amber. */
    public static void card(GuiGraphics graphics, int x, int y, int width, int height, boolean selected) {
        UiSprites.draw(graphics, selected ? UiSprites.CARD_SELECTED : UiSprites.CARD, x, y, width, height);
    }

    /**
     * An alarm: an error plate whose edge blinks (twice a second; steady with "less motion"), with an alarm icon.
     * Returns true if the text was cut.
     */
    public static boolean alarm(GuiGraphics graphics, Component text, int x, int y, int width) {
        boolean cut = plate(graphics, UiSprites.Tone.ERROR, text, x, y, width);
        boolean lit = UiSounds.reducedMotion() || (System.currentTimeMillis() / 500) % 2 == 0;
        if (lit) {
            graphics.fill(x, y, x + width, y + 1, UiColors.ERROR);
            graphics.fill(x, y + PLATE_HEIGHT - 1, x + width, y + PLATE_HEIGHT, UiColors.ERROR);
            graphics.fill(x, y, x + 2, y + PLATE_HEIGHT, UiColors.ERROR);
        }
        UiSprites.draw(graphics, UiSprites.ICON_ALARM, x + 4, y + 4, 7, 7);
        return cut;
    }

    /** A sunken screen: lists, the process, descriptions, maps. */
    public static void screen(GuiGraphics graphics, int x, int y, int width, int height) {
        UiSprites.draw(graphics, UiSprites.SCREEN, x, y, width, height);
    }

    /** A section label in capitals and the secondary colour ("INPUT", "ENERGY"). */
    public static void label(GuiGraphics graphics, Component text, int x, int y, int maxWidth) {
        UiText.line(graphics, UiText.upper(text), x, y, maxWidth, UiColors.TEXT_SECONDARY);
    }

    /** A plate with an icon and a line of text: a warning, an error, a hint. Returns true if the text was cut. */
    public static boolean plate(GuiGraphics graphics, UiSprites.Tone tone, Component text, int x, int y, int width) {
        UiSprites.draw(graphics, tone.plate, x, y, width, PLATE_HEIGHT);
        int textX = x + 5;
        if (tone.icon != null) {
            UiSprites.draw(graphics, tone.icon, x + 4, y + 4, 7, 7);
            textX = x + 15;
        }
        return UiText.line(graphics, text, textX, y + 3, x + width - 4 - textX, tone.text);
    }

    /** A status badge: a coloured dot and a word ("Working", "Waiting for energy"). Returns true if the text was cut. */
    public static boolean badge(GuiGraphics graphics, UiSprites.Tone tone, Component text, int x, int y, int width) {
        UiSprites.draw(graphics, tone.plate, x, y, width, PLATE_HEIGHT);
        UiSprites.draw(graphics, tone.dot, x + 5, y + 5, 5, 5);
        return UiText.line(graphics, text, x + 14, y + 3, width - 18, tone.text);
    }
}
