package com.projectrebirth.client.ui;

/**
 * The palette of the style "Industrial terminal" (docs/UI_STYLE.md), as 0xAARRGGBB. Windows take their colours from here,
 * never from their own constants; the sprites (blockbench/tools/build_ui.js) are painted with the same values.
 */
public final class UiColors {
    private UiColors() {
    }

    // ---- metal and screens
    public static final int BODY = 0xFF2B2E31;
    public static final int OUTLINE = 0xFF0B0C0D;
    public static final int SCREEN = 0xFF0E1311;
    public static final int SLOT = 0xFF1B1E21;

    // ---- text
    public static final int TEXT = 0xFFE6E3DC;
    public static final int TEXT_SECONDARY = 0xFFA3AAB0;
    public static final int TEXT_DIM = 0xFF6E767C;
    /** Plain text on a screen (the description). */
    public static final int SCREEN_TEXT = 0xFFC9D0D6;

    // ---- accent
    public static final int AMBER = 0xFFFFB648;
    public static final int AMBER_LIGHT = 0xFFFFCF85;
    public static final int AMBER_DIM = 0xFFC7A46A;
    public static final int AMBER_BACK = 0xFF2A2416;

    // ---- meaning
    public static final int ENERGY = 0xFF4FC3FF;
    public static final int ENERGY_DARK = 0xFF2A7FB0;
    public static final int ENERGY_TEXT = 0xFF9FDCFF;
    public static final int OK = 0xFF7BD88F;
    public static final int OK_TEXT = 0xFFB9F0C4;
    public static final int WARNING = 0xFFFFB648;
    public static final int ERROR = 0xFFFF8A7A;
    public static final int ERROR_TEXT = 0xFFFFC2B8;
    public static final int HEAT = 0xFFFFB27A;
    public static final int WEAR = 0xFFD8D0FF;
    public static final int SPARK = 0xFF4FD1C5;

    /** Integrity: green, amber at 50 % or less, red at 20 % or less. */
    public static int integrity(float fraction) {
        return fraction <= 0.2f ? ERROR : fraction <= 0.5f ? WARNING : OK;
    }

    /** The same colour with another alpha (0 to 255). */
    /**
     * 1.8.0, block K: the colour text is drawn in — in high contrast the dim and secondary greys and the dim amber one step
     * lighter, the other colours a fifth of the way to white.
     */
    public static int text(int color) {
        if (!UiSounds.highContrast()) {
            return color;
        }
        if (color == TEXT_DIM) {
            return TEXT_SECONDARY;
        }
        if (color == TEXT_SECONDARY) {
            return TEXT;
        }
        if (color == AMBER_DIM) {
            return AMBER_LIGHT;
        }
        int a = color >>> 24, r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        r += (255 - r) / 5;
        g += (255 - g) / 5;
        b += (255 - b) / 5;
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** 1.8.0, block K: the light line round a box in high contrast. */
    public static final int CONTRAST_LINE = 0xFF9AA3AA;

    public static int withAlpha(int color, int alpha) {
        return (alpha & 255) << 24 | color & 0xFFFFFF;
    }
}
