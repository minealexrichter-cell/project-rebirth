package com.projectrebirth.client.screen;

import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.garrison.MachineMountingMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The upgrade slots of a machine in the style "Industrial terminal" (1.8.0 block B): the machine's model on a screen with
 * its slots on both sides and leader lines to it; the inventory and a short help below.
 */
public class MachineMountingScreen extends UiMachineScreen<MachineMountingMenu> {
    private static final int W = 300;
    private static final int H = 235;

    public MachineMountingScreen(MachineMountingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "machine_integrity");
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        Silhouette.draw(graphics, x + 7, y + 25, W - 14, 113, menu.machine(), 18, mouseX, mouseY);
        int cx = x + W / 2;
        int count = menu.count();
        for (int i = 0; i < count; i++) {
            int sx = x + MachineMountingMenu.slotX(i, count);
            int sy = y + MachineMountingMenu.slotY(i, count);
            boolean left = sx < cx;
            boolean inner = MachineMountingMenu.slotX(i, count) == 62 || MachineMountingMenu.slotX(i, count) == 220;
            if (inner) {
                Silhouette.leader(graphics, left ? sx + 17 : sx - 2, sy + 8, left ? cx - 18 : cx + 18, sy + 8);
            }
            UiSlots.frame(graphics, UiSlots.Kind.UPGRADE, sx, sy);
            UiText.centred(graphics, Component.literal(String.valueOf(i + 1)), sx + 8, sy + 18, 18, UiColors.TEXT_DIM);
        }
        if (count == 0) {
            UiText.centred(graphics, Component.translatable("screen.projectrebirth.machine_mounting.none"), cx, y + 76, W - 40, UiColors.AMBER_DIM);
        }
        inventory(graphics, x + 8, y + MachineMountingMenu.INVENTORY_Y);
        int ix = x + 176;
        int iy = y + MachineMountingMenu.INVENTORY_Y - 11;
        int iw = W - 7 - 176;
        UiPanels.screen(graphics, ix, iy, iw, H - 7 - (MachineMountingMenu.INVENTORY_Y - 11));
        text(graphics, UiText.upper(Component.translatable("screen.projectrebirth.machine_mounting.title")), ix + 5, iy + 5, iw - 10, UiColors.AMBER);
        text(graphics, Component.translatable("screen.projectrebirth.machine_mounting.slots", count), ix + 5, iy + 17, iw - 10, UiColors.SCREEN_TEXT);
        UiText.wrapped(graphics, Component.translatable("screen.projectrebirth.machine_mounting.hint"), ix + 5, iy + 29, iw - 10, 54, UiColors.TEXT_SECONDARY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
