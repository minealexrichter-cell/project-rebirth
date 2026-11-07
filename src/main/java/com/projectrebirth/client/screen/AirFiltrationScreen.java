package com.projectrebirth.client.screen;

import java.util.Locale;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.shelter.AirFiltrationMenu;
import com.projectrebirth.shelter.AirFiltrationUnitBlockEntity;
import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.projectrebirth.client.screen.GuiShapes.panel;
import static com.projectrebirth.client.screen.GuiShapes.playerInventory;
import static com.projectrebirth.client.screen.GuiShapes.slot;

/** Window of an air filtration unit: state of the room, its air, energy, the cartridge and its slots. */
public class AirFiltrationScreen extends AbstractContainerScreen<AirFiltrationMenu> {
    private static final int ENERGY_Y = 70;
    private static final int BAR_X = 52;
    private static final int BAR_W = 92;
    private static final int GREEN = 0x3F8F3F;
    private static final int RED = 0xB03030;
    private static final int TEXT = 0x404040;

    public AirFiltrationScreen(AirFiltrationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = AirFiltrationMenu.INVENTORY_Y + 82;
        inventoryLabelY = AirFiltrationMenu.INVENTORY_Y - 11;
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
        slot(graphics, x + AirFiltrationMenu.ACTIVE_X, y + AirFiltrationMenu.SLOT_Y);
        slot(graphics, x + AirFiltrationMenu.SPARE_X, y + AirFiltrationMenu.SLOT_Y);
        GuiAtlas.outputSlot(graphics, x + AirFiltrationMenu.SPENT_X, y + AirFiltrationMenu.SLOT_Y);
        playerInventory(graphics, x + 8, y + AirFiltrationMenu.INVENTORY_Y);
        GuiAtlas.energyHorizontal(graphics, x + 8, y + ENERGY_Y, 160, 8, menu.energy(), menu.energyCapacity());
        GuiAtlas.bar(graphics, x + BAR_X, y + AirFiltrationMenu.SLOT_Y + 6, BAR_W, 9,
                menu.get(AirFiltrationUnitBlockEntity.DATA_CARTRIDGE) / 1000.0f, 0xC8B77A);
    }

    private static String hundredths(int value) {
        return String.format(Locale.ROOT, "%.2f", value / 100.0f);
    }

    /** A line of text, made smaller if it is wider than the window. */
    private void line(GuiGraphics graphics, Component text, int x, int y, int color) {
        int width = font.width(text);
        int room = imageWidth - x - 8;
        if (width <= room) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = room / (float) width;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (1.0f - scale) * font.lineHeight / 2.0f, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        int state = menu.get(AirFiltrationUnitBlockEntity.DATA_STATE);
        boolean good = state == AirFiltrationUnitBlockEntity.STATE_WORKING;
        line(graphics, Component.translatable("screen.projectrebirth.air_filtration.state." + state), 8, 18,
                good ? GREEN : state == AirFiltrationUnitBlockEntity.STATE_SCANNING ? TEXT : RED);
        int inside = menu.get(AirFiltrationUnitBlockEntity.DATA_INSIDE);
        String around = hundredths(menu.get(AirFiltrationUnitBlockEntity.DATA_AROUND));
        if (inside >= 0) {
            line(graphics, Component.translatable("screen.projectrebirth.air_filtration.room", menu.get(AirFiltrationUnitBlockEntity.DATA_VOLUME),
                    menu.get(AirFiltrationUnitBlockEntity.DATA_UNITS)), 8, 29, TEXT);
            line(graphics, Component.translatable("screen.projectrebirth.air_filtration.air", hundredths(inside), around), 8, 39, TEXT);
            line(graphics, Component.translatable("screen.projectrebirth.air_filtration.doors", menu.get(AirFiltrationUnitBlockEntity.DATA_PARTIAL)), 8, 49, TEXT);
        } else {
            line(graphics, Component.translatable("screen.projectrebirth.air_filtration.around", around), 8, 29, TEXT);
        }
        line(graphics, Component.translatable("screen.projectrebirth.air_filtration.energy", EnergyUtil.format(menu.energy()),
                menu.get(AirFiltrationUnitBlockEntity.DATA_ENERGY_NEED)), 8, 59, TEXT);
        line(graphics, Component.translatable("screen.projectrebirth.air_filtration.cartridge",
                menu.get(AirFiltrationUnitBlockEntity.DATA_CARTRIDGE) / 10), BAR_X, AirFiltrationMenu.SLOT_Y - 6, TEXT);
    }
}
