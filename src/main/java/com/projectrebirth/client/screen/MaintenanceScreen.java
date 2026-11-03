package com.projectrebirth.client.screen;

import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.technology.Maintenance;
import com.projectrebirth.technology.MaintenanceBlockEntity;
import com.projectrebirth.technology.MaintenanceMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.projectrebirth.client.screen.GuiShapes.SLOT;
import static com.projectrebirth.client.screen.GuiShapes.panel;
import static com.projectrebirth.client.screen.GuiShapes.playerInventory;
import static com.projectrebirth.client.screen.GuiShapes.slot;
import static com.projectrebirth.client.screen.GuiShapes.sunken;

/**
 * Maintenance window: materials on the left, an arrow showing the current repair step, the serviced item on the
 * right, then a status line and the integrity bars of the serviced item.
 */
public class MaintenanceScreen extends AbstractContainerScreen<MaintenanceMenu> {
    private static final ResourceLocation PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final int ARROW_X = 66;
    private static final int STATUS_Y = 46;
    private static final int BAR_Y = 58;
    private static final int BAR_X = 76;
    private static final int BAR_WIDTH = 40;

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
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        panel(graphics, x, y, imageWidth, imageHeight);
        for (int i = 0; i < MaintenanceBlockEntity.MATERIAL_SLOTS; i++) {
            slot(graphics, x + MaintenanceMenu.MATERIAL_X + i * 18, y + MaintenanceMenu.SLOT_Y);
        }
        sunken(graphics, x + MaintenanceMenu.SERVICE_X - 5, y + MaintenanceMenu.SLOT_Y - 5, 26, 26);
        playerInventory(graphics, x + 8, y + MaintenanceMenu.INVENTORY_Y);

        int ax = x + ARROW_X;
        int ay = y + MaintenanceMenu.SLOT_Y;
        graphics.fill(ax, ay + 6, ax + 15, ay + 10, SLOT);
        for (int i = 0; i < 8; i++) {
            graphics.fill(ax + 15 + i, ay + i, ax + 16 + i, ay + 16 - i, SLOT);
        }
        int width = Mth.ceil(menu.progress() * 24);
        if (width > 0) {
            graphics.blitSprite(PROGRESS_SPRITE, 24, 16, 0, 0, ax, ay, width, 16);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Component status = status();
        graphics.drawString(font, status, (imageWidth - font.width(status)) / 2, STATUS_Y, 0x404040, false);

        ItemStack stack = menu.serviced();
        switch (Maintenance.kind(stack)) {
            case ARMOR -> {
                bar(graphics, BAR_Y, Component.translatable("hud.projectrebirth.armor.structural"),
                        Maintenance.armorStructural(stack), Maintenance.maxStructural(), 0xFF4FC3F7);
                bar(graphics, BAR_Y + 10, Component.translatable("hud.projectrebirth.armor.critical"),
                        Maintenance.armorCritical(stack), Maintenance.maxCritical(), 0xFFFFA726);
            }
            case ROBOT_BODY, SYNTHETIC_BODY -> bar(graphics, BAR_Y, Component.translatable("screen.projectrebirth.maintenance.integrity"),
                    Maintenance.bodyIntegrity(stack), Maintenance.maxBodyIntegrity(stack), 0xFF66BB6A);
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
        if (!Maintenance.needsRepair(stack)) {
            return Component.translatable("screen.projectrebirth.maintenance.done");
        }
        if (Maintenance.materialFor(stack, menu::hasMaterial) == null) {
            return Component.translatable(switch (kind) {
                case ARMOR -> armorNeed(stack);
                case SYNTHETIC_BODY -> "screen.projectrebirth.maintenance.need_fiber";
                default -> "screen.projectrebirth.maintenance.need_iron";
            });
        }
        return Component.translatable("screen.projectrebirth.maintenance.repairing", Mth.floor(menu.progress() * 100));
    }

    /** Names exactly the missing armor material: plates for critical, iron for structural, or both. */
    private String armorNeed(ItemStack stack) {
        boolean plates = Maintenance.armorCritical(stack) < Maintenance.maxCritical() && !menu.hasMaterial(PowerArmorRegistry.ARMOR_PLATE.get());
        boolean iron = Maintenance.armorStructural(stack) < Maintenance.maxStructural() && !menu.hasMaterial(Items.IRON_INGOT);
        return plates && iron ? "screen.projectrebirth.maintenance.need_armor"
                : plates ? "screen.projectrebirth.maintenance.need_plates" : "screen.projectrebirth.maintenance.need_iron";
    }

    private void bar(GuiGraphics graphics, int y, Component label, float value, float max, int color) {
        graphics.drawString(font, label, 8, y, 0x404040, false);
        float fraction = max > 0 ? Mth.clamp(value / max, 0.0f, 1.0f) : 0.0f;
        graphics.fill(BAR_X, y + 1, BAR_X + BAR_WIDTH, y + 7, 0xFF373737);
        graphics.fill(BAR_X, y + 1, BAR_X + (int) (BAR_WIDTH * fraction), y + 7, color);
        graphics.drawString(font, Mth.ceil(value) + " / " + Mth.ceil(max), BAR_X + BAR_WIDTH + 4, y, 0x404040, false);
    }

}
