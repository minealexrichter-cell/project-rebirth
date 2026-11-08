package com.projectrebirth.client;

import com.projectrebirth.config.RebirthClientConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Where the HUD settings put the mod's HUD. Panel elements draw in panel coordinates after {@link #pushPanel}. */
public final class HudLayout {
    /** Size of the status panel with all its lines, at scale 1. */
    public static final int PANEL_WIDTH = 205;
    public static final int PANEL_HEIGHT = 108;
    /** The height of one line of the panel. */
    public static final int ROW = 12;
    /**
     * The last line of the panel, kept for the catastrophe in every state: human, body, armor. Below the eight lines of the
     * human panel at their fullest (1.5.0 added the temperature line).
     */
    public static final int CATASTROPHE_ROW = 98;
    /** Size of the integrity bar at scale 1. */
    public static final int BAR_WIDTH = 81;
    public static final int BAR_HEIGHT = 9;
    /** Height of the body's thin bars of wear, charge and heat (1.5.0). */
    public static final int BODY_THIN_BAR = 5;
    /** The body's whole stack at scale 1: integrity, three thin bars and the jetpack thrust, with the gaps. */
    public static final int BODY_STACK_HEIGHT = BAR_HEIGHT + 1 + 3 * (BODY_THIN_BAR + 1) + 6;
    /** The stack with its marks and up to five icons on the right. */
    public static final int BODY_STACK_WIDTH = BAR_WIDTH + 1 + 5 * 10;

    private HudLayout() {
    }

    public static boolean enabled() {
        return RebirthClientConfig.HUD_ENABLED.get();
    }

    public static float scale() {
        return RebirthClientConfig.HUD_SCALE.get().floatValue();
    }

    public static int panelX(int guiWidth) {
        return Mth.clamp(RebirthClientConfig.HUD_PANEL_X.get(), 0, Math.max(0, guiWidth - Math.round(PANEL_WIDTH * scale())));
    }

    public static int panelY(int guiHeight) {
        return Mth.clamp(RebirthClientConfig.HUD_PANEL_Y.get(), 0, Math.max(0, guiHeight - Math.round(PANEL_HEIGHT * scale())));
    }

    /** Left edge of the integrity bar drawn at scale {@link #scale()}, moved from {@code x} but kept on the screen. */
    public static int barX(int x, int guiWidth) {
        return Mth.clamp(x + RebirthClientConfig.HUD_BAR_OFFSET_X.get(), 0, Math.max(0, guiWidth - Math.round(BODY_STACK_WIDTH * scale())));
    }

    /** Bottom edge of the integrity bar, moved from {@code bottom} but kept on the screen. */
    public static int barBottom(int bottom, int guiHeight) {
        // the whole stack stays on the screen, not only the integrity bar
        int height = Math.round(BODY_STACK_HEIGHT * scale());
        return Mth.clamp(bottom + RebirthClientConfig.HUD_BAR_OFFSET_Y.get(), height, Math.max(height, guiHeight));
    }

    /** Moves drawing to the status panel; end with {@code graphics.pose().popPose()}. */
    static void pushPanel(GuiGraphics graphics) {
        float scale = scale();
        graphics.pose().pushPose();
        graphics.pose().translate(panelX(graphics.guiWidth()), panelY(graphics.guiHeight()), 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
    }
}
