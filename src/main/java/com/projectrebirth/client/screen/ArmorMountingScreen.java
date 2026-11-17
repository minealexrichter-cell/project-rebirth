package com.projectrebirth.client.screen;

import java.util.Locale;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.powerarmor.ArmorMountingMenu;
import com.projectrebirth.upgrades.ArmorSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Mounting window of a power armor: its model in the middle, a slot at each part of the hull. */
public class ArmorMountingScreen extends AbstractContainerScreen<ArmorMountingMenu> {
    public ArmorMountingScreen(ArmorMountingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, GuiShapes.fit(title, 160));
        imageWidth = 176;
        imageHeight = ArmorMountingMenu.INVENTORY_Y + 82;
        inventoryLabelY = ArmorMountingMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (ArmorSlot part : ArmorSlot.values()) {
            if (menu.getSlot(part.ordinal()).getItem().isEmpty() && GuiShapes.inside(mouseX, mouseY,
                    leftPos + ArmorMountingMenu.slotX(part), topPos + ArmorMountingMenu.slotY(part), 16, 16)) {
                graphics.renderTooltip(font, Component.translatable("screen.projectrebirth.armor_mounting."
                        + part.getSerializedName().toLowerCase(Locale.ROOT)), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiShapes.panel(graphics, x, y, imageWidth, imageHeight);
        GuiShapes.playerInventory(graphics, x + 8, y + ArmorMountingMenu.INVENTORY_Y);
        for (ArmorSlot part : ArmorSlot.values()) {
            GuiShapes.slot(graphics, x + ArmorMountingMenu.slotX(part), y + ArmorMountingMenu.slotY(part));
        }
        if (menu.armor() != null) {
            GuiAtlas.model(graphics, x + 42, y + 34, x + 134, y + 102, 25, 0.0625f, mouseX, mouseY, menu.armor());
        }
    }
}
