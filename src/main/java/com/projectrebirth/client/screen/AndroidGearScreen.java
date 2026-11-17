package com.projectrebirth.client.screen;

import com.projectrebirth.garrison.AndroidGearMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** An android's gear (1.7.0, block E): its four rows (the first slot is in its hand), its armor and its off hand. */
public class AndroidGearScreen extends AbstractContainerScreen<AndroidGearMenu> {
    public AndroidGearScreen(AndroidGearMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = AndroidGearMenu.INVENTORY_Y + 82;
        inventoryLabelY = AndroidGearMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiShapes.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                GuiShapes.slot(graphics, leftPos + 8 + col * 18, topPos + AndroidGearMenu.GEAR_Y + row * 18);
            }
        }
        graphics.fill(leftPos + 7, topPos + AndroidGearMenu.GEAR_Y - 1, leftPos + 25, topPos + AndroidGearMenu.GEAR_Y + 17, 0x6037C6E8);
        for (int i = 0; i < 5; i++) {
            GuiShapes.slot(graphics, leftPos + (i < 4 ? 8 + i * 18 : 98), topPos + AndroidGearMenu.EXTRA_Y);
        }
        GuiShapes.playerInventory(graphics, leftPos + 8, topPos + AndroidGearMenu.INVENTORY_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // two short lines between the off hand slot and the edge
        graphics.drawWordWrap(font, Component.translatable("screen.projectrebirth.android.hint"), 120, AndroidGearMenu.EXTRA_Y, imageWidth - 124, 0x404040);
    }
}
