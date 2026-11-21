package com.projectrebirth.client.screen;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiPanels;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

/**
 * The "silhouette" of the mounting windows (1.8.0 block B): a sunken screen with the model of the body, armor or machine in
 * the middle on a faint grid, and thin amber leader lines from each slot to its place on the model, so the slots stand
 * where the parts are instead of in a grid.
 */
final class Silhouette {
    private static final int GRID = 0xFF16201B;
    private static final int LEADER = UiColors.withAlpha(UiColors.AMBER, 120);

    private Silhouette() {
    }

    /** The screen with its grid and the model turning after the mouse. */
    static void draw(GuiGraphics graphics, int x, int y, int width, int height, LivingEntity model, int scale, int mouseX, int mouseY) {
        UiPanels.screen(graphics, x, y, width, height);
        for (int gx = x + 10; gx < x + width - 2; gx += 10) {
            graphics.fill(gx, y + 2, gx + 1, y + height - 2, GRID);
        }
        for (int gy = y + 10; gy < y + height - 2; gy += 10) {
            graphics.fill(x + 2, gy, x + width - 2, gy + 1, GRID);
        }
        int cx = x + width / 2;
        // a platform under the model
        graphics.fill(cx - 30, y + height - 10, cx + 30, y + height - 9, LEADER);
        if (model != null) {
            GuiAtlas.model(graphics, cx - 40, y + 4, cx + 40, y + height - 8, scale, 0.0625f, mouseX, mouseY, model);
        }
    }

    /** A leader line from a slot's edge (sx, sy) to a point of the model (tx, ty): across, then down or up. */
    static void leader(GuiGraphics graphics, int sx, int sy, int tx, int ty) {
        int left = Math.min(sx, tx);
        int right = Math.max(sx, tx);
        graphics.fill(left, sy, right + 1, sy + 1, LEADER);
        graphics.fill(tx, Math.min(sy, ty), tx + 1, Math.max(sy, ty) + 1, LEADER);
        graphics.fill(tx - 1, ty - 1, tx + 2, ty + 2, UiColors.AMBER);
    }
}
