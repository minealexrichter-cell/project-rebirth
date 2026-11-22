package com.projectrebirth.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The sprites of the interface (textures/gui/sprites/ui, painted by blockbench/tools/build_ui.js). Panels are "nine slice"
 * sprites: any size, the corners stay sharp.
 */
public final class UiSprites {
    private UiSprites() {
    }

    public static final ResourceLocation WINDOW = sprite("window");
    public static final ResourceLocation SCREEN = sprite("screen");
    public static final ResourceLocation DIVIDER = sprite("divider");
    public static final ResourceLocation HAZARD = sprite("hazard");
    public static final ResourceLocation SLOT = sprite("slot");
    public static final ResourceLocation SLOT_OUTPUT = sprite("slot_output");
    public static final ResourceLocation SLOT_MISSING = sprite("slot_missing");
    public static final ResourceLocation SLOT_DISABLED = sprite("slot_disabled");
    public static final ResourceLocation BUTTON = sprite("button");
    public static final ResourceLocation BUTTON_HOVER = sprite("button_hover");
    public static final ResourceLocation BUTTON_PRESSED = sprite("button_pressed");
    public static final ResourceLocation BUTTON_DISABLED = sprite("button_disabled");
    public static final ResourceLocation CHIP_ON = sprite("chip_on");
    public static final ResourceLocation CHIP_OFF = sprite("chip_off");
    public static final ResourceLocation CHIP_OFF_HOVER = sprite("chip_off_hover");
    public static final ResourceLocation FIELD = sprite("field");
    public static final ResourceLocation FIELD_FOCUS = sprite("field_focus");
    public static final ResourceLocation ROW_SELECTED = sprite("row_selected");
    public static final ResourceLocation ROW_HOVER = sprite("row_hover");
    public static final ResourceLocation TRACK_AMBER = sprite("track_amber");
    public static final ResourceLocation PANEL = sprite("panel");
    public static final ResourceLocation TOGGLE_ON = sprite("toggle_on");
    public static final ResourceLocation TOGGLE_OFF = sprite("toggle_off");
    public static final ResourceLocation TAB_ON = sprite("tab_on");
    public static final ResourceLocation TAB_OFF = sprite("tab_off");
    public static final ResourceLocation TAB_HOVER = sprite("tab_hover");
    public static final ResourceLocation SLOT_UPGRADE = sprite("slot_upgrade");
    public static final ResourceLocation SLOT_FUEL = sprite("slot_fuel");
    public static final ResourceLocation TOOLTIP = sprite("tooltip");
    public static final ResourceLocation CARD = sprite("card");
    public static final ResourceLocation CARD_SELECTED = sprite("card_selected");
    public static final ResourceLocation TOAST = sprite("toast");

    /** 7 x 7 icons (the dots are 5 x 5, the station mark 9 x 9). */
    public static final ResourceLocation ICON_CHECK = sprite("icon/check");
    public static final ResourceLocation ICON_CROSS = sprite("icon/cross");
    public static final ResourceLocation ICON_WARNING = sprite("icon/warning");
    public static final ResourceLocation ICON_WARNING_AMBER = sprite("icon/warning_amber");
    public static final ResourceLocation ICON_SEARCH = sprite("icon/search");
    public static final ResourceLocation ICON_CLOSE = sprite("icon/close");
    public static final ResourceLocation ICON_HELP = sprite("icon/help");
    public static final ResourceLocation ICON_STATION = sprite("icon/station");
    /** 7 x 4 arrows of drop-down lists and sorting. */
    public static final ResourceLocation ICON_ARROW_DOWN = sprite("icon/arrow_down");
    public static final ResourceLocation ICON_ARROW_UP = sprite("icon/arrow_up");
    /** 7 x 7. */
    public static final ResourceLocation ICON_INFO = sprite("icon/info");
    public static final ResourceLocation ICON_ALARM = sprite("icon/alarm");

    /** The kinds of status plates and status dots: each has its colour, frame and icon. */
    public enum Tone {
        OK("plate_ok", "icon/dot_ok", "icon/check", UiColors.OK_TEXT),
        WARNING("plate_warning", "icon/dot_warning", "icon/warning_amber", UiColors.AMBER_LIGHT),
        ERROR("plate_error", "icon/dot_error", "icon/warning", UiColors.ERROR_TEXT),
        INFO("plate_info", "icon/dot_idle", "icon/info", UiColors.TEXT_SECONDARY);

        public final ResourceLocation plate;
        public final ResourceLocation dot;
        public final ResourceLocation icon;
        public final int text;

        Tone(String plate, String dot, String icon, int text) {
            this.plate = sprite(plate);
            this.dot = sprite(dot);
            this.icon = icon == null ? null : sprite(icon);
            this.text = text;
        }
    }

    public static void draw(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        // 1.8.0, block K: the same pieces as GuiGraphics.blitSprite lays out (nine slice, tile, stretch), in one draw call
        var sprites = net.minecraft.client.Minecraft.getInstance().getGuiSprites();
        net.minecraft.client.renderer.texture.TextureAtlasSprite atlas = sprites.getSprite(sprite);
        net.minecraft.client.resources.metadata.gui.GuiSpriteScaling scaling = sprites.getSpriteScaling(atlas);
        RenderSystem.enableBlend();
        UiTexQuads quads = UiTexQuads.begin(graphics, atlas.atlasLocation());
        if (scaling instanceof net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.NineSlice nine) {
            nineSlice(quads, atlas, nine, x, y, width, height);
        } else if (scaling instanceof net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.Tile tile) {
            tiled(quads, atlas, tile.width(), tile.height(), 0, 0, x, y, width, height, tile.width(), tile.height());
        } else {
            quads.quad(x, y, x + width, y + height, atlas.getU0(), atlas.getV0(), atlas.getU1(), atlas.getV1());
        }
        quads.end();
        RenderSystem.disableBlend();
    }

    /** As the game's blitNineSlicedSprite: the corners as they are, the edges and the middle repeated. */
    private static void nineSlice(UiTexQuads quads, net.minecraft.client.renderer.texture.TextureAtlasSprite atlas,
                                  net.minecraft.client.resources.metadata.gui.GuiSpriteScaling.NineSlice nine, int x, int y, int width, int height) {
        int sw = nine.width(), sh = nine.height();
        var border = nine.border();
        int left = Math.min(border.left(), width / 2), right = Math.min(border.right(), width / 2);
        int top = Math.min(border.top(), height / 2), bottom = Math.min(border.bottom(), height / 2);
        if (width == sw && height == sh) {
            tiled(quads, atlas, sw, sh, 0, 0, x, y, width, height, sw, sh);
            return;
        }
        // corners
        tiled(quads, atlas, sw, sh, 0, 0, x, y, left, top, left, top);
        tiled(quads, atlas, sw, sh, sw - right, 0, x + width - right, y, right, top, right, top);
        tiled(quads, atlas, sw, sh, 0, sh - bottom, x, y + height - bottom, left, bottom, left, bottom);
        tiled(quads, atlas, sw, sh, sw - right, sh - bottom, x + width - right, y + height - bottom, right, bottom, right, bottom);
        // edges and the middle
        int innerW = width - left - right, innerH = height - top - bottom, tileW = sw - left - right, tileH = sh - top - bottom;
        tiled(quads, atlas, sw, sh, left, 0, x + left, y, innerW, top, tileW, top);
        tiled(quads, atlas, sw, sh, left, sh - bottom, x + left, y + height - bottom, innerW, bottom, tileW, bottom);
        tiled(quads, atlas, sw, sh, 0, top, x, y + top, left, innerH, left, tileH);
        tiled(quads, atlas, sw, sh, sw - right, top, x + width - right, y + top, right, innerH, right, tileH);
        tiled(quads, atlas, sw, sh, left, top, x + left, y + top, innerW, innerH, tileW, tileH);
    }

    /**
     * An area filled with a piece of the sprite (its pixels from (u, v), tileW x tileH) repeated, the last ones cut; sw x sh is
     * the sprite's size in pixels.
     */
    private static void tiled(UiTexQuads quads, net.minecraft.client.renderer.texture.TextureAtlasSprite atlas, int sw, int sh,
                              int u, int v, int x, int y, int width, int height, int tileW, int tileH) {
        if (width <= 0 || height <= 0 || tileW <= 0 || tileH <= 0) {
            return;
        }
        float u0 = atlas.getU0(), du = atlas.getU1() - u0, v0 = atlas.getV0(), dv = atlas.getV1() - v0;
        for (int down = 0; down < height; down += tileH) {
            int h = Math.min(tileH, height - down);
            for (int across = 0; across < width; across += tileW) {
                int w = Math.min(tileW, width - across);
                quads.quad(x + across, y + down, x + across + w, y + down + h, u0 + du * u / sw, v0 + dv * v / sh,
                        u0 + du * (u + w) / sw, v0 + dv * (v + h) / sh);
            }
        }
    }

    private static ResourceLocation sprite(String name) {
        return ProjectRebirth.id("ui/" + name);
    }
}
