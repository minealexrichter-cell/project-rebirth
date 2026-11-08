package com.projectrebirth.client.screen;

import com.projectrebirth.client.GuiAtlas;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.Maintenance;
import com.projectrebirth.technology.MaintenanceBlockEntity;
import com.projectrebirth.technology.MaintenanceMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.projectrebirth.client.screen.GuiShapes.panel;
import static com.projectrebirth.client.screen.GuiShapes.playerInventory;
import static com.projectrebirth.client.screen.GuiShapes.slot;

/**
 * Maintenance window: materials on the left, an arrow showing the current repair step, the serviced item on the
 * right, then a status line and the integrity bars of the serviced item.
 */
public class MaintenanceScreen extends AbstractContainerScreen<MaintenanceMenu> {
    private static final int ARROW_X = 66;
    private static final int STATUS_Y = 46;
    private static final int BAR_Y = 58;
    private static final int BAR_X = 76;
    private static final int ENERGY_X = 167;
    private static final int ENERGY_Y = 16;
    private static final int ENERGY_W = 6;
    private static final int ENERGY_H = 66;
    private static final int BAR_WIDTH = 40;
    private static final int EMPTY_HINT_WIDTH = 150;

    public MaintenanceScreen(MaintenanceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = MaintenanceMenu.INVENTORY_Y + 82;
        inventoryLabelY = MaintenanceMenu.INVENTORY_Y - 11;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (GuiShapes.inside(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H)) {
            graphics.renderComponentTooltip(font, StationScreen.energyTooltip(menu.energy(), menu.energyCapacity(),
                    MaintenanceBlockEntity.energyPerTick()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        panel(graphics, x, y, imageWidth, imageHeight);
        for (int i = 0; i < MaintenanceBlockEntity.MATERIAL_SLOTS; i++) {
            slot(graphics, x + MaintenanceMenu.MATERIAL_X + i * 18, y + MaintenanceMenu.SLOT_Y);
        }
        GuiAtlas.outputSlot(graphics, x + MaintenanceMenu.SERVICE_X - 5, y + MaintenanceMenu.SLOT_Y - 5);
        playerInventory(graphics, x + 8, y + MaintenanceMenu.INVENTORY_Y);
        GuiShapes.energyBar(graphics, x + ENERGY_X, y + ENERGY_Y, ENERGY_W, ENERGY_H, menu.energy(), menu.energyCapacity());

        GuiAtlas.arrow(graphics, x + ARROW_X, y + MaintenanceMenu.SLOT_Y, menu.progress());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Component status = status();
        ItemStack stack = menu.serviced();
        if (Maintenance.kind(stack) == Maintenance.Kind.NONE) {
            // No bars below in an empty station, so the hint may take two lines to stay inside the window.
            int lineY = STATUS_Y;
            for (FormattedCharSequence line : font.split(status, EMPTY_HINT_WIDTH)) {
                graphics.drawString(font, line, (imageWidth - font.width(line)) / 2, lineY, 0x404040, false);
                lineY += font.lineHeight + 1;
            }
        } else {
            graphics.drawString(font, status, (imageWidth - font.width(status)) / 2, STATUS_Y, 0x404040, false);
        }

        switch (Maintenance.kind(stack)) {
            case ARMOR -> {
                bar(graphics, BAR_Y, Component.translatable("hud.projectrebirth.armor.structural"),
                        Maintenance.armorStructural(stack), Maintenance.maxStructural(stack), 0xFF4FC3F7);
                bar(graphics, BAR_Y + 10, Component.translatable("hud.projectrebirth.armor.critical"),
                        Maintenance.armorCritical(stack), Maintenance.maxCritical(stack), 0xFFFFA726);
            }
            case ROBOT_BODY, SYNTHETIC_BODY -> {
                bar(graphics, BAR_Y, Component.translatable("screen.projectrebirth.maintenance.integrity"),
                        Maintenance.bodyIntegrity(stack), Maintenance.maxBodyIntegrity(stack), 0xFF66BB6A);
                // wear (1.5.0): the bar fills with wear, empty = none
                bar(graphics, BAR_Y + 10, Component.translatable("screen.projectrebirth.maintenance.wear"),
                        Maintenance.bodyWear(stack), 100.0f, 0xFFA1887F);
            }
            default -> {
            }
        }
    }

    private Component status() {
        ItemStack stack = menu.serviced();
        Maintenance.Kind kind = Maintenance.kind(stack);
        if (kind == Maintenance.Kind.NONE) {
            return Component.translatable("screen.projectrebirth.maintenance.empty");
        }
        if (menu.emiPaused() && Maintenance.needsRepair(stack)) {
            return Component.translatable("screen.projectrebirth.station.emi");
        }
        if (!Maintenance.needsRepair(stack)) {
            return Component.translatable("screen.projectrebirth.maintenance.done");
        }
        if (Maintenance.materialFor(stack, menu::hasMaterial) == null) {
            return Component.translatable(switch (kind) {
                case ARMOR -> armorNeed(stack);
                case SYNTHETIC_BODY -> Maintenance.bodyIntegrity(stack) < Maintenance.maxBodyIntegrity(stack)
                        ? "screen.projectrebirth.maintenance.need_fiber" : "screen.projectrebirth.maintenance.need_board";
                default -> Maintenance.bodyIntegrity(stack) < Maintenance.maxBodyIntegrity(stack)
                        ? "screen.projectrebirth.maintenance.need_iron" : "screen.projectrebirth.maintenance.need_actuator";
            });
        }
        if (menu.energy() < MaintenanceBlockEntity.energyPerTick()) {
            return Component.translatable("screen.projectrebirth.station.no_energy");
        }
        return Component.translatable("screen.projectrebirth.maintenance.repairing", Mth.floor(menu.progress() * 100));
    }

    /** Names exactly the missing armor material: plates for critical, iron for structural, or both. */
    private String armorNeed(ItemStack stack) {
        boolean plates = Maintenance.armorCritical(stack) < Maintenance.maxCritical(stack) && !menu.hasMaterial(PowerArmorRegistry.ARMOR_PLATE.get());
        boolean iron = Maintenance.armorStructural(stack) < Maintenance.maxStructural(stack) && !menu.hasMaterial(Items.IRON_INGOT);
        return plates && iron ? "screen.projectrebirth.maintenance.need_armor"
                : plates ? "screen.projectrebirth.maintenance.need_plates" : "screen.projectrebirth.maintenance.need_iron";
    }

    private void bar(GuiGraphics graphics, int y, Component label, float value, float max, int color) {
        graphics.drawString(font, label, 8, y, 0x404040, false);
        float fraction = max > 0 ? Mth.clamp(value / max, 0.0f, 1.0f) : 0.0f;
        GuiAtlas.bar(graphics, BAR_X, y + 1, BAR_WIDTH, 6, fraction, color);
        graphics.drawString(font, Mth.ceil(value) + " / " + Mth.ceil(max), BAR_X + BAR_WIDTH + 4, y, 0x404040, false);
    }

}
