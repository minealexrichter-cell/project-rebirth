package com.projectrebirth.client.screen;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.GeneratorMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import static com.projectrebirth.client.screen.GuiShapes.panel;
import static com.projectrebirth.client.screen.GuiShapes.playerInventory;
import static com.projectrebirth.client.screen.GuiShapes.slot;

/** Combustion generator window: fuel slot with the furnace flame, energy gauge and state line. */
public class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {
    private static final ResourceLocation FLAME_SPRITE = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
    private static final int FLAME_X = GeneratorMenu.FUEL_X + 1;
    private static final int FLAME_Y = GeneratorMenu.FUEL_Y - 18;
    private static final int ENERGY_X = 52;
    private static final int ENERGY_Y = 22;
    private static final int ENERGY_W = 116;
    private static final int ENERGY_H = 14;

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = GeneratorMenu.INVENTORY_Y + 82;
        inventoryLabelY = GeneratorMenu.INVENTORY_Y - 11;
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
        panel(graphics, x, y, imageWidth, imageHeight);
        slot(graphics, x + GeneratorMenu.FUEL_X, y + GeneratorMenu.FUEL_Y);
        playerInventory(graphics, x + 8, y + GeneratorMenu.INVENTORY_Y);
        if (menu.burning()) {
            int height = Mth.ceil(menu.burn() * 13.0f) + 1;
            graphics.blitSprite(FLAME_SPRITE, 14, 14, 0, 14 - height, x + FLAME_X, y + FLAME_Y + 14 - height, 14, height);
        }
        // Horizontal gauge, filled from the left.
        GuiShapes.sunken(graphics, x + ENERGY_X, y + ENERGY_Y, ENERGY_W, ENERGY_H);
        int capacity = menu.energyCapacity();
        int filled = capacity <= 0 ? 0 : (int) Math.round((double) (ENERGY_W - 2) * Math.min(menu.energy(), capacity) / capacity);
        if (filled > 0) {
            graphics.fill(x + ENERGY_X + 1, y + ENERGY_Y + 1, x + ENERGY_X + 1 + filled, y + ENERGY_Y + ENERGY_H - 1, GuiShapes.ENERGY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Component energy = Component.translatable("screen.projectrebirth.energy",
                EnergyUtil.format(menu.energy()), EnergyUtil.format(menu.energyCapacity()));
        graphics.drawString(font, energy, 8, 58, 0x404040, false);
        Component state;
        if (menu.burning() && menu.energy() >= menu.energyCapacity()) {
            state = Component.translatable("screen.projectrebirth.generator.full");
        } else if (menu.burning()) {
            state = Component.translatable("screen.projectrebirth.generator.burning", RebirthConfig.COMBUSTION_ENERGY_PER_TICK.get());
        } else {
            state = Component.translatable("screen.projectrebirth.generator.no_fuel");
        }
        graphics.drawString(font, state, 8, 69, 0x606060, false);
    }
}
