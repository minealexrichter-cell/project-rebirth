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
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.energy.CombustionGeneratorBlockEntity;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.GeneratorMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Combustion generator window in the style "Industrial terminal" (1.8.0 block B): the fuel slot, a screen with the fire,
 * the burn left and what it makes, the energy column, the state, and what the fuel in the slot will give.
 */
public class GeneratorScreen extends UiMachineScreen<GeneratorMenu> {
    private static final int W = 300;
    private static final int H = 214;
    private static final int SCREEN_X = 40;
    private static final int SCREEN_Y = 36;
    private static final int SCREEN_W = 140;
    private static final int SCREEN_H = 60;
    private static final int RIGHT_X = 187;

    private final UiEnergy energy = new UiEnergy();
    private final UiBars.Smooth burn = new UiBars.Smooth();

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "combustion_generator");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        energy.tick(menu.energy(), 0);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.generator." + key, args);
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        ItemStack fuel = menu.getSlot(0).getItem();
        boolean burning = menu.burning();
        boolean full = burning && menu.energy() >= menu.energyCapacity();
        int makes = burning && !full ? RebirthConfig.COMBUSTION_ENERGY_PER_TICK.get() : 0;

        // fuel
        UiPanels.label(graphics, tr("label.fuel"), x + 8, y + 26, 150);
        UiSlots.frame(graphics, UiSlots.Kind.FUEL, x + GeneratorMenu.FUEL_X, y + GeneratorMenu.FUEL_Y);

        // the fire screen
        int sx = x + SCREEN_X;
        int sy = y + SCREEN_Y;
        UiPanels.screen(graphics, sx, sy, SCREEN_W, SCREEN_H);
        Component header = Component.literal("> ").append(burning ? tr("process.burning") : tr("process.idle"));
        text(graphics, header, sx + 6, sy + 5, SCREEN_W - 12, burning ? UiColors.AMBER : UiColors.AMBER_DIM);
        drawFire(graphics, sx + 8, sy + 18, burning);
        float left = burn.follow(menu.burn());
        UiText.scaled(graphics, Component.literal(Mth.ceil(left * 100) + "%"), sx + 62, sy + 18, 2.0f, burning ? UiColors.AMBER : UiColors.AMBER_DIM);
        text(graphics, tr("left_fuel"), sx + 62, sy + 36, SCREEN_W - 68, UiColors.AMBER_DIM);
        UiBars.progress(graphics, sx + 6, sy + SCREEN_H - 10, SCREEN_W - 12, 5, left, UiColors.AMBER);

        // energy and state
        UiPanels.label(graphics, Component.translatable("screen.projectrebirth.station.label.energy"), x + RIGHT_X, y + 26, W - 7 - RIGHT_X);
        energy.draw(this, graphics, x + RIGHT_X, y + 36, W - 7 - RIGHT_X, 46, menu.energy(), menu.energyCapacity());
        UiSprites.Tone tone;
        Component badge;
        Component plate;
        if (full) {
            tone = UiSprites.Tone.WARNING;
            badge = tr("state.full");
            plate = tr("plate.full");
        } else if (burning) {
            tone = UiSprites.Tone.OK;
            badge = tr("state.burning");
            plate = tr("plate.burning", makes);
        } else {
            tone = UiSprites.Tone.ERROR;
            badge = tr("state.no_fuel");
            plate = tr("plate.no_fuel");
        }
        badge(graphics, tone, badge, x + RIGHT_X, y + 88, W - 7 - RIGHT_X);
        plate(graphics, tone == UiSprites.Tone.OK ? UiSprites.Tone.INFO : tone, plate, x + 7, y + 104, W - 14);

        // inventory and what the fuel gives
        inventory(graphics, x + GeneratorMenu.INVENTORY_X, y + GeneratorMenu.INVENTORY_Y);
        int ix = x + 176;
        int iy = y + 121;
        int iw = W - 7 - 176;
        UiPanels.screen(graphics, ix, iy, iw, H - 7 - iy + y);
        text(graphics, UiText.upper(tr("label.fuel")), ix + 5, iy + 5, iw - 10, UiColors.AMBER);
        if (fuel.isEmpty()) {
            UiText.wrapped(graphics, tr("info.empty"), ix + 5, iy + 17, iw - 10, 54, UiColors.SCREEN_TEXT);
        } else {
            int ticks = CombustionGeneratorBlockEntity.burnTime(fuel);
            int perTick = RebirthConfig.COMBUSTION_ENERGY_PER_TICK.get();
            text(graphics, fuel.getHoverName(), ix + 5, iy + 17, iw - 10, UiColors.SCREEN_TEXT);
            text(graphics, tr("info.one", clock(ticks)), ix + 5, iy + 29, iw - 10, UiColors.TEXT_SECONDARY);
            text(graphics, tr("info.gives", EnergyUtil.format(ticks * perTick)), ix + 5, iy + 39, iw - 10, UiColors.ENERGY_TEXT);
            text(graphics, tr("info.stack", fuel.getCount(), clock(ticks * fuel.getCount())), ix + 5, iy + 51, iw - 10, UiColors.TEXT_SECONDARY);
        }
        text(graphics, tr("info.automation"), ix + 5, y + H - 18, iw - 10, UiColors.TEXT_DIM);
    }

    @Override
    protected void drawOver(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.getSlot(0).getItem().isEmpty()) {
            UiSlots.ghost(graphics, new ItemStack(Items.COAL), x + GeneratorMenu.FUEL_X, y + GeneratorMenu.FUEL_Y);
        }
    }

    /** Flames over a grate that flicker while it burns; cold coals otherwise. 46 x 34. */
    private void drawFire(GuiGraphics graphics, int x, int y, boolean burning) {
        int amber = UiColors.AMBER;
        graphics.fill(x, y + 32, x + 46, y + 34, amber);
        for (int i = 0; i < 5; i++) {
            graphics.fill(x + 3 + i * 9, y + 28, x + 8 + i * 9, y + 31, burning ? 0xFFFF7A3C : UiColors.withAlpha(UiColors.AMBER, 90));
        }
        if (!burning) {
            return;
        }
        long time = UiSounds.animations() ? System.currentTimeMillis() : 0L;
        for (int i = 0; i < 5; i++) {
            float flicker = (Mth.sin(time / 120.0f + i * 1.7f) + 1.0f) * 0.5f;
            int h = 10 + Math.round(flicker * 12) + (i == 2 ? 6 : 0);
            int fx = x + 4 + i * 9;
            graphics.fill(fx, y + 28 - h, fx + 4, y + 28, amber);
            graphics.fill(fx + 1, y + 28 - h + 4, fx + 3, y + 28, UiColors.AMBER_LIGHT);
        }
    }

    private static String clock(int ticks) {
        int seconds = (ticks + 19) / 20;
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
