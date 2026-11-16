package com.projectrebirth.client.screen;

import com.projectrebirth.garrison.MachineMountingMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The upgrade slots of a machine (1.7.0): one row of slots for armor upgrades above the player's inventory. */
public class MachineMountingScreen extends AbstractContainerScreen<MachineMountingMenu> {
    public MachineMountingScreen(MachineMountingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = MachineMountingMenu.INVENTORY_Y + 82;
        inventoryLabelY = MachineMountingMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiShapes.panel(graphics, x, y, imageWidth, imageHeight);
        GuiShapes.playerInventory(graphics, x + 8, y + MachineMountingMenu.INVENTORY_Y);
        for (int i = 0; i < menu.count(); i++) {
            GuiShapes.slot(graphics, x + MachineMountingMenu.slotX(i, menu.count()), y + 36);
        }
        Component hint = Component.translatable("screen.projectrebirth.machine_mounting.hint");
        graphics.drawString(font, hint, x + (imageWidth - font.width(hint)) / 2, y + 60, 0x404040, false);
    }
}
