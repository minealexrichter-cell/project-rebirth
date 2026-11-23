package com.projectrebirth.client.screen;

import java.util.List;

import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.wanderers.WandererData;
import com.projectrebirth.wanderers.WandererTradeMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 1.9.0, block E: the trade of a group of wanderers, in the style of the mod's windows: cards of offers in two columns (what
 * you give, an arrow, what you get), dimmed when you lack the cost; a click on a card trades. The player's inventory below.
 */
public class WandererTradeScreen extends UiMachineScreen<WandererTradeMenu> {
    private static final int W = 176;
    private static final int H = 234;
    private static final int CARD_W = 78;
    private static final int CARD_H = 28;
    private static final int TOP = 24;

    public WandererTradeScreen(WandererTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "trade");
    }

    private int cardX(int index) {
        return leftPos + 8 + (index % 2) * (CARD_W + 4);
    }

    private int cardY(int index) {
        return topPos + TOP + (index / 2) * (CARD_H + 2);
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        List<WandererData.Trade> offers = menu.offers();
        if (offers.isEmpty()) {
            text(graphics, Component.translatable("screen.projectrebirth.wanderer_trade.nothing"), x + 10, y + TOP + 6, W - 20, UiColors.TEXT_DIM);
        }
        var player = Minecraft.getInstance().player;
        for (int i = 0; i < offers.size() && i < 8; i++) {
            WandererData.Trade offer = offers.get(i);
            int cx = cardX(i), cy = cardY(i);
            boolean hover = mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H;
            boolean affordable = player != null && WandererTradeMenu.canAfford(player, offer);
            UiPanels.card(graphics, cx, cy, CARD_W, CARD_H, hover && affordable);
            int ix = cx + 4;
            for (ItemStack cost : offer.cost()) {
                graphics.renderItem(cost, ix, cy + 6);
                graphics.renderItemDecorations(font, cost, ix, cy + 6);
                ix += 18;
            }
            graphics.drawString(font, "→", ix + 1, cy + 10, affordable ? UiColors.AMBER : UiColors.TEXT_DIM, false);
            graphics.renderItem(offer.result(), cx + CARD_W - 21, cy + 6);
            graphics.renderItemDecorations(font, offer.result(), cx + CARD_W - 21, cy + 6);
            if (!affordable) {
                graphics.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + CARD_H - 1, 0x66101418);
            }
            final int index = i;
            if (hover) {
                hints.add(cx, cy, CARD_W, CARD_H, Component.translatable(affordable ? "screen.projectrebirth.wanderer_trade.click"
                        : "screen.projectrebirth.wanderer_trade.cannot", offers.get(index).result().getHoverName()));
            }
        }
        inventory(graphics, x + WandererTradeMenu.INVENTORY_X, y + WandererTradeMenu.INVENTORY_Y);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<WandererData.Trade> offers = menu.offers();
            for (int i = 0; i < offers.size() && i < 8; i++) {
                int cx = cardX(i), cy = cardY(i);
                if (mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H) {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WandererTradeMenu.ACTION_TRADE + i);
                        com.projectrebirth.client.ui.UiSounds.CLICK.play();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
