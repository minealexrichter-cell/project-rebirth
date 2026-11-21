package com.projectrebirth.client.screen;

import java.util.Locale;

import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.powerarmor.ArmorMountingMenu;
import com.projectrebirth.upgrades.ArmorSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Mounting window of a power armor in the style "Industrial terminal" (1.8.0 block B): the armor's model on a screen, each
 * upgrade slot at its part of the hull with its name and a leader line to the model; the inventory and a short help below.
 */
public class ArmorMountingScreen extends UiMachineScreen<ArmorMountingMenu> {
    private static final int W = 300;
    private static final int H = 235;
    private static final int SCREEN_Y = 25;
    private static final int SCREEN_H = 113;

    public ArmorMountingScreen(ArmorMountingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "armor_upgrades");
    }

    private static Component part(ArmorSlot part) {
        return Component.translatable("screen.projectrebirth.armor_mounting." + part.getSerializedName().toLowerCase(Locale.ROOT));
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        Silhouette.draw(graphics, x + 7, y + SCREEN_Y, W - 14, SCREEN_H, menu.armor(), 25, mouseX, mouseY);
        int cx = x + W / 2;
        for (ArmorSlot part : ArmorSlot.values()) {
            int sx = x + ArmorMountingMenu.slotX(part);
            int sy = y + ArmorMountingMenu.slotY(part);
            if (part != ArmorSlot.HEAD) {
                boolean left = sx < cx;
                Silhouette.leader(graphics, left ? sx + 17 : sx - 2, sy + 8, left ? cx - 16 : cx + 16, sy + 8);
            }
            UiSlots.frame(graphics, UiSlots.Kind.UPGRADE, sx, sy);
            UiText.centred(graphics, part(part), sx + 8, sy + 18, 76, UiColors.TEXT_SECONDARY);
            if (menu.getSlot(part.ordinal()).getItem().isEmpty()) {
                tooltip(sx, sy, 16, 16, () -> UiTooltip.of(part(part)).text(Component.translatable("screen.projectrebirth.armor_mounting.slot_hint"), UiColors.TEXT_SECONDARY));
            }
        }
        inventory(graphics, x + 8, y + ArmorMountingMenu.INVENTORY_Y);
        int ix = x + 176;
        int iy = y + ArmorMountingMenu.INVENTORY_Y - 11;
        UiPanels.screen(graphics, ix, iy, W - 7 - 176, H - 7 - (ArmorMountingMenu.INVENTORY_Y - 11));
        text(graphics, UiText.upper(Component.translatable("screen.projectrebirth.armor_mounting.title")), ix + 5, iy + 5, W - 7 - 176 - 10, UiColors.AMBER);
        UiText.wrapped(graphics, Component.translatable("screen.projectrebirth.armor_mounting.help"), ix + 5, iy + 17, W - 7 - 176 - 10, 64, UiColors.SCREEN_TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
