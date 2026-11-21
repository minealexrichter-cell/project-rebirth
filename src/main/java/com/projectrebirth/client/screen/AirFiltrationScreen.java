package com.projectrebirth.client.screen;

import java.util.Locale;

import com.projectrebirth.client.ui.UiBars;
import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiEnergy;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSlots;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.client.ui.UiTooltip;
import com.projectrebirth.shelter.AirFiltrationMenu;
import com.projectrebirth.shelter.AirFiltrationUnitBlockEntity;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Air filtration unit in the style "Industrial terminal" (1.8.0 block D): a screen with the room (its volume, the units,
 * the air inside against the air around, the iron doors on its border) and a fan that turns while it cleans; the
 * cartridges (working, spare, spent) with the working one's life; energy; the state and what to do.
 */
public class AirFiltrationScreen extends UiMachineScreen<AirFiltrationMenu> {
    private static final int W = 320;
    private static final int H = 226;
    private static final int RIGHT_X = 213;

    private final UiEnergy energy = new UiEnergy();

    public AirFiltrationScreen(AirFiltrationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "sealed_room");
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.air_filtration." + key, args);
    }

    private static String hundredths(int value) {
        return String.format(Locale.ROOT, "%.2f", value / 100.0f);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean working = menu.get(AirFiltrationUnitBlockEntity.DATA_STATE) == AirFiltrationUnitBlockEntity.STATE_WORKING;
        energy.tick(menu.energy(), working ? menu.get(AirFiltrationUnitBlockEntity.DATA_ENERGY_NEED) : 0);
    }

    private static UiSprites.Tone tone(int state) {
        return switch (state) {
            case AirFiltrationUnitBlockEntity.STATE_WORKING -> UiSprites.Tone.OK;
            case AirFiltrationUnitBlockEntity.STATE_SCANNING -> UiSprites.Tone.INFO;
            case AirFiltrationUnitBlockEntity.STATE_NO_ENERGY, AirFiltrationUnitBlockEntity.STATE_SPENT_FULL -> UiSprites.Tone.WARNING;
            default -> UiSprites.Tone.ERROR;
        };
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int state = menu.get(AirFiltrationUnitBlockEntity.DATA_STATE);
        boolean working = state == AirFiltrationUnitBlockEntity.STATE_WORKING;
        // the room
        int sx = x + 7;
        int sy = y + 25;
        int sw = 200;
        UiPanels.screen(graphics, sx, sy, sw, 82);
        text(graphics, Component.literal("> ").append(UiText.upper(tr("process"))), sx + 5, sy + 5, sw - 10, working ? UiColors.AMBER : UiColors.AMBER_DIM);
        drawFan(graphics, sx + 8, sy + 20, working);
        int tx = sx + 56;
        int tw = sw - 61;
        int inside = menu.get(AirFiltrationUnitBlockEntity.DATA_INSIDE);
        int around = menu.get(AirFiltrationUnitBlockEntity.DATA_AROUND);
        if (inside >= 0) {
            text(graphics, tr("room", menu.get(AirFiltrationUnitBlockEntity.DATA_VOLUME), menu.get(AirFiltrationUnitBlockEntity.DATA_UNITS)), tx, sy + 18, tw, UiColors.SCREEN_TEXT);
            float scale = Math.max(1.0f, Math.max(inside, around) / 100.0f);
            UiBars.meter(graphics, null, tx, sy + 30, tw, inside / 100.0f / scale, inside < around ? UiColors.OK : UiColors.WARNING,
                    Component.literal(hundredths(inside)));
            UiBars.meter(graphics, null, tx, sy + 42, tw, around / 100.0f / scale, UiColors.HEAT, Component.literal(hundredths(around)));
            tooltip(tx, sy + 30, tw, 21, () -> UiTooltip.of(tr("air_title")).row(tr("inside"), Component.literal(hundredths(inside)), UiColors.OK)
                    .row(tr("outside"), Component.literal(hundredths(around)), UiColors.HEAT));
            text(graphics, tr("doors", menu.get(AirFiltrationUnitBlockEntity.DATA_PARTIAL)), tx, sy + 56, tw, UiColors.TEXT_SECONDARY);
        } else {
            text(graphics, tr("around", hundredths(around)), tx, sy + 18, tw, UiColors.SCREEN_TEXT);
            UiText.wrapped(graphics, tr("no_room"), tx, sy + 30, tw, 36, UiColors.TEXT_SECONDARY);
        }
        // cartridges
        int rx = x + RIGHT_X;
        UiPanels.label(graphics, tr("label.cartridges"), rx, y + 26, W - 7 - RIGHT_X);
        UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + AirFiltrationMenu.ACTIVE_X, y + AirFiltrationMenu.SLOT_Y);
        UiSlots.frame(graphics, UiSlots.Kind.INPUT, x + AirFiltrationMenu.SPARE_X, y + AirFiltrationMenu.SLOT_Y);
        UiSlots.frame(graphics, UiSlots.Kind.OUTPUT, x + AirFiltrationMenu.SPENT_X, y + AirFiltrationMenu.SLOT_Y);
        float life = menu.get(AirFiltrationUnitBlockEntity.DATA_CARTRIDGE) / 1000.0f;
        UiBars.meter(graphics, null, rx, y + 66, W - 7 - RIGHT_X, life, life > 0.2f ? 0xFFC8B77A : UiColors.ERROR, Component.literal(Mth.floor(life * 100) + "%"));
        // energy
        energy.draw(this, graphics, rx, y + 80, W - 7 - RIGHT_X, 26, menu.energy(), menu.energyCapacity());
        // state
        plate(graphics, tone(state), tr("todo." + state), x + 7, y + 112, W - 14);
        // inventory and help
        inventory(graphics, x + 8, y + AirFiltrationMenu.INVENTORY_Y);
        int ix = x + 176;
        int iy = y + AirFiltrationMenu.INVENTORY_Y - 11;
        int iw = W - 7 - 176;
        UiPanels.screen(graphics, ix, iy, iw, H - 7 - (AirFiltrationMenu.INVENTORY_Y - 11));
        text(graphics, UiText.upper(tr("state." + state)), ix + 5, iy + 5, iw - 10, tone(state).text);
        UiText.wrapped(graphics, tr("help"), ix + 5, iy + 17, iw - 10, 66, UiColors.TEXT_SECONDARY);
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.getSlot(0).getItem().isEmpty()) {
            UiSlots.ghostLabel(graphics, tr("ghost.active"), x + AirFiltrationMenu.ACTIVE_X, y + AirFiltrationMenu.SLOT_Y);
        }
        if (menu.getSlot(1).getItem().isEmpty()) {
            UiSlots.ghostLabel(graphics, tr("ghost.spare"), x + AirFiltrationMenu.SPARE_X, y + AirFiltrationMenu.SLOT_Y);
        }
    }

    /** A fan of four blades in a duct, turning while it cleans. 40 x 40. */
    private void drawFan(GuiGraphics graphics, int x, int y, boolean working) {
        int amber = UiColors.AMBER;
        int cx = x + 20;
        int cy = y + 20;
        graphics.fill(x, y, x + 40, y + 1, amber);
        graphics.fill(x, y + 39, x + 40, y + 40, amber);
        graphics.fill(x, y, x + 1, y + 40, amber);
        graphics.fill(x + 39, y, x + 40, y + 40, amber);
        float turn = working && com.projectrebirth.client.ui.UiSounds.animations() ? (System.currentTimeMillis() % 800L) / 800.0f * Mth.TWO_PI : 0.3f;
        for (int blade = 0; blade < 4; blade++) {
            float a = turn + blade * Mth.HALF_PI;
            for (int r = 3; r < 16; r++) {
                int px = cx + Math.round(Mth.cos(a) * r);
                int py = cy + Math.round(Mth.sin(a) * r);
                graphics.fill(px, py, px + 2, py + 2, working ? amber : UiColors.AMBER_DIM);
            }
        }
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, UiColors.AMBER_LIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
