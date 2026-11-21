package com.projectrebirth.client.screen;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.garrison.AndroidEntity;
import com.projectrebirth.garrison.AndroidGearMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An android's gear in the style "Industrial terminal" (1.7.0, block E; 1.8.0, block E): its body in the middle of a screen with the
 * armor slots beside it from head to feet and the off hand under them, its pack of four rows (the first slot is what it holds) and
 * the player's inventory on the right.
 */
public class AndroidGearScreen extends UiMachineScreen<AndroidGearMenu> {
    private static final int W = AndroidGearMenu.INVENTORY_X + 162 + 7;
    private static final int H = AndroidGearMenu.INVENTORY_Y + 83;
    private static final ItemStack[] ARMOR_GHOSTS = {new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
            new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS)};

    public AndroidGearScreen(AndroidGearMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "androids");
    }

    private static Component tr(String key) {
        return Component.translatable("screen.projectrebirth.android." + key);
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // the body
        int mx = x + 7, my = y + 25, mw = 72, mh = 96;
        UiPanels.screen(graphics, mx, my, mw, mh);
        AndroidEntity android = menu.android();
        if (android != null) {
            graphics.enableScissor(mx + 2, my + 2, mx + mw - 2, my + mh - 2);
            GuiAtlas.model(graphics, mx, my + 4, mx + mw, my + mh - 2, 38, 0.0625f, mouseX, mouseY, android);
            graphics.disableScissor();
        }
        for (int i = 0; i < 4; i++) {
            UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + AndroidGearMenu.ARMOR_X, y + AndroidGearMenu.ARMOR_Y + i * 18);
        }
        UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + AndroidGearMenu.ARMOR_X, y + AndroidGearMenu.OFFHAND_Y);
        // the pack: four rows, the first slot in its hand
        UiPanels.label(graphics, tr("pack"), x + AndroidGearMenu.GEAR_X - 1, y + 26, 160);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                UiSlots.frame(graphics, row == 0 && col == 0 ? UiSlots.Kind.UPGRADE : UiSlots.Kind.INPUT,
                        x + AndroidGearMenu.GEAR_X + col * 18, y + AndroidGearMenu.GEAR_Y + row * 18);
            }
        }
        inventory(graphics, x + AndroidGearMenu.INVENTORY_X, y + AndroidGearMenu.INVENTORY_Y);
        int hy = y + 125;
        UiPanels.screen(graphics, x + 7, hy, 96, H - 7 - 125);
        UiText.wrapped(graphics, tr("hint"), x + 11, hy + 4, 88, H - 7 - 125 - 8, UiColors.TEXT_SECONDARY);
        tooltip(x + AndroidGearMenu.GEAR_X - 1, y + AndroidGearMenu.GEAR_Y - 1, 18, 18,
                () -> com.projectrebirth.client.ui.UiTooltip.of(tr("hand")).text(tr("hand_hint"), UiColors.TEXT_SECONDARY));
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < 4; i++) {
            // the menu's armor slots run from the feet up; the window shows them from the head down
            if (menu.getSlot(36 + 3 - i).getItem().isEmpty()) {
                UiSlots.ghost(graphics, ARMOR_GHOSTS[i], x + AndroidGearMenu.ARMOR_X, y + AndroidGearMenu.ARMOR_Y + i * 18);
            }
        }
        if (menu.getSlot(40).getItem().isEmpty()) {
            UiSlots.ghost(graphics, new ItemStack(Items.SHIELD), x + AndroidGearMenu.ARMOR_X, y + AndroidGearMenu.OFFHAND_Y);
        }
    }
}
