package com.projectrebirth.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Slot frames of the style. Positions are those of the item (as in a menu's Slot): the 18 px frame is drawn 1 px around
 * it, the big output frame 5 px around it. An empty slot can show a "ghost": a faint item or icon telling what goes there.
 */
public final class UiSlots {
    public enum Kind {
        INPUT(UiSprites.SLOT, 1, 18),
        OUTPUT(UiSprites.SLOT_OUTPUT, 5, 26),
        UPGRADE(UiSprites.SLOT_UPGRADE, 1, 18),
        FUEL(UiSprites.SLOT_FUEL, 1, 18),
        DISABLED(UiSprites.SLOT_DISABLED, 1, 18),
        MISSING(UiSprites.SLOT_MISSING, 1, 18);

        private final ResourceLocation sprite;
        private final int margin;
        private final int size;

        Kind(ResourceLocation sprite, int margin, int size) {
            this.sprite = sprite;
            this.margin = margin;
            this.size = size;
        }
    }

    private UiSlots() {
    }

    /** The frame of a slot whose item sits at (itemX, itemY). */
    public static void frame(GuiGraphics graphics, Kind kind, int itemX, int itemY) {
        UiSprites.draw(graphics, kind.sprite, itemX - kind.margin, itemY - kind.margin, kind.size, kind.size);
    }

    /** A faint item in an empty slot: what is meant to go there. */
    public static void ghost(GuiGraphics graphics, ItemStack stack, int itemX, int itemY) {
        graphics.renderFakeItem(stack, itemX, itemY);
        graphics.fill(RenderType.guiGhostRecipeOverlay(), itemX, itemY, itemX + 16, itemY + 16, 0xA01B1E21);
    }

    /** A faint caption (two or three letters) in an empty slot, when there is no item to show. */
    public static void ghostLabel(GuiGraphics graphics, Component label, int itemX, int itemY) {
        int width = Minecraft.getInstance().font.width(label);
        float scale = Math.min(1.0f, 15.0f / Math.max(1, width));
        UiText.scaled(graphics, label, itemX + 8 - width * scale / 2, itemY + 8 - 4 * scale, scale, UiColors.withAlpha(UiColors.TEXT_SECONDARY, 90));
    }

    /** A count in the slot's corner in a colour ("0/2" in red for a missing ingredient). */
    public static void count(GuiGraphics graphics, Component text, int itemX, int itemY, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 200.0f);
        UiText.scaled(graphics, text, itemX + 17 - Minecraft.getInstance().font.width(text) * 0.8f, itemY + 10, 0.8f, color);
        graphics.pose().popPose();
    }
}
