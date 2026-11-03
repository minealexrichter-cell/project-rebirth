package com.projectrebirth.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/** Vanilla-looking panels and slots drawn from plain rectangles, shared by the mod's windows. */
final class GuiShapes {
    static final int PANEL = 0xFFC6C6C6;
    static final int LIGHT = 0xFFFFFFFF;
    static final int SHADOW = 0xFF555555;
    static final int SLOT = 0xFF8B8B8B;
    static final int SLOT_SHADOW = 0xFF373737;

    private GuiShapes() {
    }

    static final int ENERGY = 0xFFE0502A;
    static final int ENERGY_TOP = 0xFFFF9A5C;

    /** A vertical energy gauge filling up from the bottom. */
    static void energyBar(GuiGraphics graphics, int x, int y, int width, int height, int energy, int capacity) {
        sunken(graphics, x, y, width, height);
        int inner = height - 2;
        int filled = capacity <= 0 ? 0 : (int) Math.round((double) inner * Math.min(energy, capacity) / capacity);
        if (filled > 0) {
            graphics.fill(x + 1, y + height - 1 - filled, x + width - 1, y + height - 1, ENERGY);
            graphics.fill(x + 1, y + height - 1 - filled, x + width - 1, y + height - filled, ENERGY_TOP);
        }
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width - 1, y + 1, LIGHT);
        graphics.fill(x, y, x + 1, y + height - 1, LIGHT);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, SHADOW);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, SHADOW);
    }

    /** Standard 18 px slot frame around an item drawn at (itemX, itemY). */
    static void slot(GuiGraphics graphics, int itemX, int itemY) {
        sunken(graphics, itemX - 1, itemY - 1, 18, 18);
    }

    static void sunken(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SLOT);
        graphics.fill(x, y, x + width - 1, y + 1, SLOT_SHADOW);
        graphics.fill(x, y, x + 1, y + height - 1, SLOT_SHADOW);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, LIGHT);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, LIGHT);
    }

    /** Player inventory (three rows) and hotbar slot frames, with the inventory's top-left item at (x, y). */
    static void playerInventory(GuiGraphics graphics, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(graphics, x + col * 18, y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(graphics, x + col * 18, y + 58);
        }
    }
}
