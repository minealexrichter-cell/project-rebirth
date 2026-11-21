package com.projectrebirth.client.ui;

import com.projectrebirth.technology.energy.EnergyUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The energy column of a machine window (1.8.0): a vertical gauge with the stock, the capacity, the income and the use per
 * tick. The server sends only the stock, so the income is told from how the stock changed over the last second plus what
 * the machine used meanwhile. {@link #tick} is called once a client tick from the window's containerTick.
 */
public final class UiEnergy {
    private final int[] history = new int[21];
    private final UiBars.Smooth shown = new UiBars.Smooth();
    private int ticks;
    private int use;

    /** Records the stock of this tick and what the machine uses a tick right now (0 when it does not work). */
    public void tick(int energy, int usePerTick) {
        ticks++;
        history[ticks % history.length] = energy;
        use = usePerTick;
    }

    public int use() {
        return use;
    }

    /** Income per tick over the last second (never below 0). */
    public int income() {
        if (ticks < history.length) {
            return 0;
        }
        int now = history[ticks % history.length];
        int before = history[(ticks + 1) % history.length];
        return Math.max(0, Math.round((now - before) / (float) (history.length - 1)) + use);
    }

    /** Net change per tick over the last second (output of a battery or generator shows as a negative number). */
    public int net() {
        if (ticks < history.length) {
            return 0;
        }
        return Math.round((history[ticks % history.length] - history[(ticks + 1) % history.length]) / (float) (history.length - 1));
    }

    /**
     * Draws the column: the gauge (width 12) and, right of it, the stock, "of capacity FE", "+income FE/t" and "−use FE/t".
     */
    public void draw(UiMachineScreen<?> screen, GuiGraphics graphics, int x, int y, int width, int gaugeHeight, int energy, int capacity) {
        float fraction = capacity <= 0 ? 0.0f : shown.follow(Math.min(1.0f, energy / (float) capacity));
        UiBars.vertical(graphics, x, y, 12, gaugeHeight, fraction, UiColors.ENERGY, UiColors.ENERGY_DARK);
        int tx = x + 17;
        int tw = width - 17;
        screen.text(graphics, Component.literal(EnergyUtil.format(energy)), tx, y + 1, tw, UiColors.ENERGY_TEXT);
        screen.text(graphics, Component.translatable("screen.projectrebirth.station.energy.of", EnergyUtil.format(capacity)), tx, y + 11, tw, UiColors.TEXT_SECONDARY);
        if (gaugeHeight >= 40) {
            screen.text(graphics, Component.translatable("screen.projectrebirth.station.energy.in", EnergyUtil.format(income())), tx, y + 25, tw, UiColors.OK);
            screen.text(graphics, Component.translatable("screen.projectrebirth.station.energy.out", EnergyUtil.format(use)), tx, y + 35, tw,
                    use > 0 ? UiColors.WARNING : UiColors.TEXT_DIM);
        }
        screen.tooltip(x, y, 12, gaugeHeight, () -> UiTooltip.of(Component.translatable("screen.projectrebirth.station.label.energy"))
                .row(Component.translatable("screen.projectrebirth.ui.energy.stock"), Component.literal(EnergyUtil.format(energy) + " / " + EnergyUtil.format(capacity) + " FE"), UiColors.ENERGY_TEXT)
                .row(Component.translatable("screen.projectrebirth.ui.energy.income"), Component.translatable("screen.projectrebirth.station.energy.in", EnergyUtil.format(income())), UiColors.OK)
                .row(Component.translatable("screen.projectrebirth.ui.energy.use"), Component.translatable("screen.projectrebirth.station.energy.out", EnergyUtil.format(use)), UiColors.WARNING)
                .text(Component.translatable("screen.projectrebirth.ui.energy.note"), UiColors.TEXT_DIM));
    }
}
