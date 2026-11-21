package com.projectrebirth.client.garrison;

import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiEnergy;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.MachineKind;
import com.projectrebirth.garrison.ServicePostMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The window of a service post in the style "Industrial terminal" (1.8.0, block E): its four plates seen from above with the machine
 * on them, what is done to it (charge, repair), the post's energy and what the chest next to it holds for the service.
 */
public class ServicePostScreen extends UiMachineScreen<ServicePostMenu> {
    private static final int W = 300;
    private static final int H = 204;
    private final UiEnergy energy = new UiEnergy();

    public ServicePostScreen(ServicePostMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "service_post");
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.service_post." + key, args);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        energy.tick(menu.energy(), 0);
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        boolean plates = menu.get(ServicePostMenu.DATA_PLATES) != 0;
        int served = menu.get(ServicePostMenu.DATA_SERVED);
        int kind = menu.get(ServicePostMenu.DATA_KIND);
        // the plates from above
        int sx = x + 7, sy = y + 25, sw = 140, sh = 110;
        UiPanels.screen(graphics, sx, sy, sw, sh);
        text(graphics, Component.literal("> ").append(UiText.upper(tr("plates"))), sx + 5, sy + 5, sw - 10, UiColors.AMBER_DIM);
        int px = sx + sw / 2 - 26, py = sy + 24;
        if (plates) {
            for (int i = 0; i < 4; i++) {
                int bx = px + (i % 2) * 27, by = py + (i / 2) * 27;
                graphics.fill(bx, by, bx + 25, by + 25, 0xFF2B3330);
                graphics.renderOutline(bx, by, 25, 25, served > 0 ? UiColors.AMBER : 0xFF4A5550);
            }
            if (kind >= 0) {
                graphics.pose().pushPose();
                graphics.pose().translate(px + 26 - 16, py + 26 - 16, 0);
                graphics.pose().scale(2.0f, 2.0f, 1.0f);
                graphics.renderItem(ConsoleScreen.icon(MachineKind.values()[Mth.clamp(kind, 0, MachineKind.values().length - 1)]), 0, 0);
                graphics.pose().popPose();
            }
        } else {
            UiText.wrapped(graphics, tr("no_plates"), sx + 6, sy + 30, sw - 12, 60, UiColors.AMBER_DIM);
        }
        // the machine on the plates
        int rx = x + 153, rw = W - 7 - 153;
        UiPanels.label(graphics, tr("on_plates"), rx, y + 26, rw);
        if (kind >= 0) {
            MachineKind machine = MachineKind.values()[Mth.clamp(kind, 0, MachineKind.values().length - 1)];
            text(graphics, Component.translatable("screen.projectrebirth.console.kind." + machine.id()).copy().append(served > 1 ? " +" + (served - 1) : ""), rx, y + 37, rw, UiColors.TEXT);
            float structural = menu.get(ServicePostMenu.DATA_MAX_STRUCTURAL) <= 0 ? 0
                    : menu.get(ServicePostMenu.DATA_STRUCTURAL) / (float) menu.get(ServicePostMenu.DATA_MAX_STRUCTURAL);
            UiBars.meter(graphics, null, rx, y + 49, rw, structural, 0xFF000000 | UiColors.integrity(structural), Component.literal(Math.round(structural * 100) + "%"));
            int charge = menu.get(ServicePostMenu.DATA_CHARGE);
            if (charge >= 0) {
                UiBars.meter(graphics, null, rx, y + 61, rw, charge / 100.0f, UiColors.ENERGY, Component.literal(charge + "%"));
            }
        } else {
            text(graphics, tr("nobody"), rx, y + 37, rw, UiColors.TEXT_DIM);
        }
        UiPanels.label(graphics, Component.translatable("screen.projectrebirth.station.label.energy"), rx, y + 76, rw);
        energy.draw(this, graphics, rx, y + 87, rw, 26, menu.energy(), menu.energyCapacity());
        // the chest
        int cy = y + 140;
        UiPanels.label(graphics, tr("stock"), x + 8, cy, W - 16);
        if (menu.get(ServicePostMenu.DATA_CHEST) == 0) {
            text(graphics, tr("no_chest"), x + 8, cy + 13, W - 16, UiColors.WARNING);
        } else {
            stock(graphics, new ItemStack(Items.IRON_INGOT), menu.get(ServicePostMenu.DATA_IRON), x + 8, cy + 10);
            stock(graphics, new ItemStack(com.projectrebirth.powerarmor.PowerArmorRegistry.ARMOR_PLATE.get()), menu.get(ServicePostMenu.DATA_PLATES_ITEM),
                    x + 80, cy + 10);
            stock(graphics, new ItemStack(com.projectrebirth.weapons.WeaponRegistry.ASSAULT_RIFLE_AMMO.get()), menu.get(ServicePostMenu.DATA_ROUNDS),
                    x + 152, cy + 10);
            stock(graphics, new ItemStack(GarrisonRegistry.ROCKET.get()), menu.get(ServicePostMenu.DATA_ROCKETS), x + 224, cy + 10);
        }
        // the state
        UiSprites.Tone tone;
        String state;
        if (!plates) {
            tone = UiSprites.Tone.ERROR;
            state = "no_plates_short";
        } else if (menu.energy() <= 0) {
            tone = UiSprites.Tone.WARNING;
            state = "no_energy";
        } else if (served > 0) {
            tone = UiSprites.Tone.OK;
            state = "serving";
        } else {
            tone = UiSprites.Tone.INFO;
            state = "waiting";
        }
        plate(graphics, tone, tr("state." + state), x + 7, y + H - 21, W - 14);
    }

    private void stock(GuiGraphics graphics, ItemStack stack, int count, int x, int y) {
        graphics.renderItem(stack, x, y);
        text(graphics, Component.literal("× " + count), x + 19, y + 4, 48, count > 0 ? UiColors.SCREEN_TEXT : UiColors.TEXT_DIM);
        tooltip(x, y, 66, 16, () -> com.projectrebirth.client.ui.UiTooltip.of(stack.getHoverName()).text(tr("stock_hint"), UiColors.TEXT_SECONDARY));
    }
}
