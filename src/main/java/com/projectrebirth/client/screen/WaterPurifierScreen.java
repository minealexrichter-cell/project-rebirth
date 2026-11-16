package com.projectrebirth.client.screen;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.water.WaterPurifierBlockEntity;
import com.projectrebirth.water.WaterPurifierMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import static com.projectrebirth.client.screen.GuiShapes.panel;
import static com.projectrebirth.client.screen.GuiShapes.playerInventory;
import static com.projectrebirth.client.screen.GuiShapes.slot;

/** Water purifier window: polluted water, the cartridge, clean water, with the furnace's arrows between, energy and state. */
public class WaterPurifierScreen extends AbstractContainerScreen<WaterPurifierMenu> {
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

    public WaterPurifierScreen(WaterPurifierMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = WaterPurifierMenu.INVENTORY_Y + 82;
        inventoryLabelY = WaterPurifierMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        panel(graphics, x, y, imageWidth, imageHeight);
        slot(graphics, x + WaterPurifierMenu.INPUT_X, y + WaterPurifierMenu.SLOT_Y);
        slot(graphics, x + WaterPurifierMenu.CARTRIDGE_X, y + WaterPurifierMenu.SLOT_Y);
        slot(graphics, x + WaterPurifierMenu.OUTPUT_X, y + WaterPurifierMenu.SLOT_Y);
        playerInventory(graphics, x + 8, y + WaterPurifierMenu.INVENTORY_Y);
        // the water's way through: an arrow before the cartridge and one after it, filling as the work goes
        int width = Mth.ceil(menu.progress() * 24.0f);
        int first = Math.min(width * 2, 14), second = Math.max(0, width * 2 - 24) * 14 / 24;
        if (first > 0) {
            graphics.blitSprite(ARROW, 24, 16, 0, 0, x + WaterPurifierMenu.INPUT_X + 19, y + WaterPurifierMenu.SLOT_Y, first, 16);
        }
        if (second > 0) {
            graphics.blitSprite(ARROW, 24, 16, 0, 0, x + WaterPurifierMenu.CARTRIDGE_X + 19, y + WaterPurifierMenu.SLOT_Y, Math.min(14, second), 16);
        }
        GuiAtlas.energyHorizontal(graphics, x + 8, y + 50, 160, 8, menu.energy(), menu.energyCapacity());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Component energy = Component.translatable("screen.projectrebirth.energy", EnergyUtil.format(menu.energy()), EnergyUtil.format(menu.energyCapacity()));
        graphics.drawString(font, energy, 8, 61, 0x404040, false);
        String key = switch (menu.state()) {
            case WaterPurifierBlockEntity.STATE_WORKING -> "working";
            case WaterPurifierBlockEntity.STATE_NO_CARTRIDGE -> "no_cartridge";
            case WaterPurifierBlockEntity.STATE_NO_ENERGY -> "no_energy";
            case WaterPurifierBlockEntity.STATE_OUTPUT_FULL -> "output_full";
            default -> "idle";
        };
        Component state = Component.translatable("screen.projectrebirth.water_purifier." + key);
        graphics.drawString(font, state, 8, 71, 0x606060, false);
        if (menu.cartridge() > 0) {
            Component life = Component.translatable("screen.projectrebirth.water_purifier.cartridge", menu.cartridge() / 10);
            graphics.drawString(font, life, imageWidth - 8 - font.width(life), titleLabelY, 0x606060, false);
        }
    }
}
