package com.projectrebirth.client.screen;

import com.projectrebirth.client.GuiAtlas;

import net.minecraft.client.gui.GuiGraphics;

/** The panels and slots of the mod's windows, drawn from the mod's own texture (see GuiAtlas). */
final class GuiShapes {
    private GuiShapes() {
    }

    /** A vertical energy gauge filling up from the bottom. */
    static void energyBar(GuiGraphics graphics, int x, int y, int width, int height, int energy, int capacity) {
        GuiAtlas.energyVertical(graphics, x, y, width, height, energy, capacity);
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        GuiAtlas.panel(graphics, x, y, width, height);
    }

    /** Standard 18 px slot frame around an item drawn at (itemX, itemY). */
    static void slot(GuiGraphics graphics, int itemX, int itemY) {
        GuiAtlas.slot(graphics, itemX - 1, itemY - 1);
    }

    static void sunken(GuiGraphics graphics, int x, int y, int width, int height) {
        GuiAtlas.recess(graphics, x, y, width, height);
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
