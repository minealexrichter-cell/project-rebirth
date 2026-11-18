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
        RenderSystem.enableBlend();
        graphics.blitSprite(sprite, x, y, width, height);
        RenderSystem.disableBlend();
    }

    private static ResourceLocation sprite(String name) {
        return ProjectRebirth.id("ui/" + name);
    }
}
