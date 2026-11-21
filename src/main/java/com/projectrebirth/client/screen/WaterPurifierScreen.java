package com.projectrebirth.client.screen;

import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiEnergy;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.water.WaterPurifierBlockEntity;
import com.projectrebirth.water.WaterPurifierMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Water purifier in the style "Industrial terminal" (1.8.0 block D): the water goes left to right across the screen,
 * polluted water, the cartridge, clean water, with drops running through the pipe while it works; the cartridge's life,
 * energy, the state and what to do.
 */
public class WaterPurifierScreen extends UiMachineScreen<WaterPurifierMenu> {
    private static final int W = 300;
    private static final int H = 214;

    private final UiEnergy energy = new UiEnergy();

    public WaterPurifierScreen(WaterPurifierMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "polluted_water");
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.water_purifier." + key, args);
    }

    private String stateKey() {
        return switch (menu.state()) {
            case WaterPurifierBlockEntity.STATE_WORKING -> "working";
            case WaterPurifierBlockEntity.STATE_NO_CARTRIDGE -> "no_cartridge";
            case WaterPurifierBlockEntity.STATE_NO_ENERGY -> "no_energy";
            case WaterPurifierBlockEntity.STATE_OUTPUT_FULL -> "output_full";
            default -> "idle";
        };
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        energy.tick(menu.energy(), 0);
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        String key = stateKey();
        boolean working = menu.state() == WaterPurifierBlockEntity.STATE_WORKING;
        UiSprites.Tone tone = switch (menu.state()) {
            case WaterPurifierBlockEntity.STATE_WORKING -> UiSprites.Tone.OK;
            case WaterPurifierBlockEntity.STATE_NO_CARTRIDGE -> UiSprites.Tone.ERROR;
            case WaterPurifierBlockEntity.STATE_NO_ENERGY, WaterPurifierBlockEntity.STATE_OUTPUT_FULL -> UiSprites.Tone.WARNING;
            default -> UiSprites.Tone.INFO;
        };
        // the way of the water
        int sx = x + 7;
        int sy = y + 25;
        int sw = 186;
        UiPanels.screen(graphics, sx, sy, sw, 76);
        text(graphics, Component.literal("> ").append(UiText.upper(tr(working ? "working" : "process"))), sx + 5, sy + 5, sw - 10, working ? UiColors.AMBER : UiColors.AMBER_DIM);
        int pipeY = y + WaterPurifierMenu.SLOT_Y + 7;
        graphics.fill(x + WaterPurifierMenu.INPUT_X + 18, pipeY, x + WaterPurifierMenu.OUTPUT_X - 2, pipeY + 3, 0xFF2A332E);
        float progress = menu.progress();
        int filled = Math.round((WaterPurifierMenu.OUTPUT_X - WaterPurifierMenu.INPUT_X - 20) * progress);
        graphics.fill(x + WaterPurifierMenu.INPUT_X + 18, pipeY, x + WaterPurifierMenu.INPUT_X + 18 + filled, pipeY + 3, UiColors.ENERGY);
        if (working && UiSounds.animations()) {
            int span = WaterPurifierMenu.OUTPUT_X - WaterPurifierMenu.INPUT_X - 22;
            for (int i = 0; i < 4; i++) {
                int dx = (int) ((System.currentTimeMillis() / 40 + i * span / 4) % span);
                graphics.fill(x + WaterPurifierMenu.INPUT_X + 19 + dx, pipeY - 1, x + WaterPurifierMenu.INPUT_X + 21 + dx, pipeY + 4, 0xFFBFEFFF);
            }
        }
        UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + WaterPurifierMenu.INPUT_X, y + WaterPurifierMenu.SLOT_Y);
        UiSlots.frame(graphics, UiSlots.Kind.UPGRADE, x + WaterPurifierMenu.CARTRIDGE_X, y + WaterPurifierMenu.SLOT_Y);
        UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + WaterPurifierMenu.OUTPUT_X, y + WaterPurifierMenu.SLOT_Y);
        UiText.centred(graphics, tr("label.dirty"), x + WaterPurifierMenu.INPUT_X + 8, y + WaterPurifierMenu.SLOT_Y + 19, 60, UiColors.TEXT_SECONDARY);
        UiText.centred(graphics, tr("label.filter"), x + WaterPurifierMenu.CARTRIDGE_X + 8, y + WaterPurifierMenu.SLOT_Y + 19, 60, UiColors.TEXT_SECONDARY);
        UiText.centred(graphics, tr("label.clean"), x + WaterPurifierMenu.OUTPUT_X + 8, y + WaterPurifierMenu.SLOT_Y + 19, 60, UiColors.TEXT_SECONDARY);
        UiBars.progress(graphics, sx + 6, sy + 66, sw - 12, 5, progress, UiColors.AMBER);
        // cartridge and energy on the right
        int rx = x + 199;
        int rw = W - 7 - 199;
        UiPanels.label(graphics, tr("label.cartridge"), rx, y + 26, rw);
        float life = menu.cartridge() / 1000.0f;
        UiBars.meter(graphics, null, rx, y + 37, rw, life, life > 0.2f ? 0xFFC8B77A : UiColors.ERROR, Component.literal(Mth.floor(life * 100) + "%"));
        UiPanels.label(graphics, Component.translatable("screen.projectrebirth.station.label.energy"), rx, y + 52, rw);
        energy.draw(this, graphics, rx, y + 62, rw, 26, menu.energy(), menu.energyCapacity());
        plate(graphics, tone, tr("todo." + key), x + 7, y + 106, W - 14);
        inventory(graphics, x + 8, y + WaterPurifierMenu.INVENTORY_Y);
        int ix = x + 176;
        int iy = y + WaterPurifierMenu.INVENTORY_Y - 11;
        int iw = W - 7 - 176;
        UiPanels.screen(graphics, ix, iy, iw, H - 7 - (WaterPurifierMenu.INVENTORY_Y - 11));
        UiText.wrapped(graphics, tr("help"), ix + 5, iy + 5, iw - 10, H - 7 - (WaterPurifierMenu.INVENTORY_Y - 11) - 10, UiColors.TEXT_SECONDARY);
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.getSlot(0).getItem().isEmpty()) {
            UiSlots.ghost(graphics, new ItemStack(Items.WATER_BUCKET), x + WaterPurifierMenu.INPUT_X, y + WaterPurifierMenu.SLOT_Y);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
