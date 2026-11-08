package com.projectrebirth.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The mod's window and HUD parts, all from one texture (textures/gui/widgets.png, painted by
 * blockbench/tools/build_textures.js). Parts that can have any size repeat their middle, so the corners stay sharp.
 */
public final class GuiAtlas {
    private static final ResourceLocation TEXTURE = ProjectRebirth.id("textures/gui/widgets.png");
    private static final int SIZE = 256;

    private GuiAtlas() {
    }

    /** The panel behind a whole window. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        nineSlice(graphics, x, y, width, height, 0, 0, 32, 8);
    }

    /** A slot frame around an item drawn at (x + 1, y + 1). */
    public static void slot(GuiGraphics graphics, int x, int y) {
        blit(graphics, x, y, 32, 0, 18, 18);
    }

    /** The larger frame of an output slot, 26 x 26. */
    public static void outputSlot(GuiGraphics graphics, int x, int y) {
        blit(graphics, x, y, 52, 0, 26, 26);
    }

    /** A recess of any size: list rows and the inside of gauges. */
    public static void recess(GuiGraphics graphics, int x, int y, int width, int height) {
        nineSlice(graphics, x, y, width, height, 80, 0, 12, 3);
    }

    /** The 24 x 16 progress arrow, filling from the left. */
    public static void arrow(GuiGraphics graphics, int x, int y, float progress) {
        blit(graphics, x, y, 0, 34, 24, 16);
        int width = Mth.ceil(Mth.clamp(progress, 0.0f, 1.0f) * 24);
        if (width > 0) {
            blit(graphics, x, y, 0, 52, width, 16);
        }
    }

    /** The small 16 x 11 arrow of the inventory's crafting grid. */
    public static void smallArrow(GuiGraphics graphics, int x, int y) {
        blit(graphics, x, y, 60, 34, 16, 11);
    }

    /** The 9 x 9 mark of a sealed body. */
    public static void sealIcon(GuiGraphics graphics, int x, int y) {
        blit(graphics, x, y, 80, 34, 9, 9);
    }

    /** The 9 x 9 mark of a hazard: polluted air, radiation, acid. */
    public static void hazardMark(GuiGraphics graphics, int x, int y, com.projectrebirth.environment.Hazard hazard) {
        int u = switch (hazard) {
            case POLLUTION -> 92;
            case RADIATION -> 102;
            case ACID -> 112;
            case DUST -> 92; // placeholder: the polluted air mark until block H draws its own
        };
        blit(graphics, x, y, u, 34, 9, 9);
    }

    /** An energy gauge filling up from the bottom. */
    public static void energyVertical(GuiGraphics graphics, int x, int y, int width, int height, int energy, int capacity) {
        recess(graphics, x, y, width, height);
        int filled = filled(height - 2, energy, capacity);
        for (int done = 0; done < filled; done += 4) {
            int part = Math.min(4, filled - done);
            for (int across = 0; across < width - 2; across += 8) {
                blit(graphics, x + 1 + across, y + height - 1 - done - part, 30, 34, Math.min(8, width - 2 - across), part);
            }
        }
    }

    /** An energy gauge filling up from the left. */
    public static void energyHorizontal(GuiGraphics graphics, int x, int y, int width, int height, int energy, int capacity) {
        recess(graphics, x, y, width, height);
        int filled = filled(width - 2, energy, capacity);
        for (int done = 0; done < filled; done += 4) {
            int part = Math.min(4, filled - done);
            for (int down = 0; down < height - 2; down += 8) {
                blit(graphics, x + 1 + done, y + 1 + down, 40, 34, part, Math.min(8, height - 2 - down));
            }
        }
    }

    /** A status bar with notches, in the given colour (0xAARRGGBB; the alpha is ignored). */
    public static void bar(GuiGraphics graphics, int x, int y, int width, int height, float fraction, int color) {
        nineSlice(graphics, x, y, width, height, 96, 0, 9, 2);
        int filled = Math.round((width - 2) * Mth.clamp(fraction, 0.0f, 1.0f));
        graphics.setColor((color >> 16 & 255) / 255.0f, (color >> 8 & 255) / 255.0f, (color & 255) / 255.0f, 1.0f);
        for (int done = 0; done < filled; done += 8) {
            blit(graphics, x + 1 + done, y + 1, 50, 34, Math.min(8, filled - done), Math.min(7, height - 2));
        }
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** A body or armor in a window: it turns after the mouse and otherwise stands still (see WindowPose). */
    public static void model(GuiGraphics graphics, int x1, int y1, int x2, int y2, int scale, float yOffset,
                             float mouseX, float mouseY, LivingEntity entity) {
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, scale, yOffset, mouseX, mouseY, entity);
    }

    private static int filled(int size, int energy, int capacity) {
        return capacity <= 0 ? 0 : (int) Math.round((double) size * Math.min(energy, capacity) / capacity);
    }

    private static void blit(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        RenderSystem.enableBlend();
        graphics.blit(TEXTURE, x, y, u, v, width, height, SIZE, SIZE);
        RenderSystem.disableBlend();
    }

    private static void nineSlice(GuiGraphics graphics, int x, int y, int width, int height, int u, int v, int size, int corner) {
        int middle = size - 2 * corner;
        int innerWidth = width - 2 * corner;
        int innerHeight = height - 2 * corner;
        int far = size - corner;
        blit(graphics, x, y, u, v, corner, corner);
        blit(graphics, x + width - corner, y, u + far, v, corner, corner);
        blit(graphics, x, y + height - corner, u, v + far, corner, corner);
        blit(graphics, x + width - corner, y + height - corner, u + far, v + far, corner, corner);
        tile(graphics, x + corner, y, innerWidth, corner, u + corner, v, middle, corner);
        tile(graphics, x + corner, y + height - corner, innerWidth, corner, u + corner, v + far, middle, corner);
        tile(graphics, x, y + corner, corner, innerHeight, u, v + corner, corner, middle);
        tile(graphics, x + width - corner, y + corner, corner, innerHeight, u + far, v + corner, corner, middle);
        tile(graphics, x + corner, y + corner, innerWidth, innerHeight, u + corner, v + corner, middle, middle);
    }

    private static void tile(GuiGraphics graphics, int x, int y, int width, int height, int u, int v, int tileWidth, int tileHeight) {
        for (int down = 0; down < height; down += tileHeight) {
            for (int across = 0; across < width; across += tileWidth) {
                blit(graphics, x + across, y + down, u, v, Math.min(tileWidth, width - across), Math.min(tileHeight, height - down));
            }
        }
    }
}
