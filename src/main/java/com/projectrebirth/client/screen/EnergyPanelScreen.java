package com.projectrebirth.client.screen;

import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiEnergy;
import com.projectrebirth.client.ui.UiMachineScreen;
import com.projectrebirth.client.ui.UiPanels;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.client.ui.UiSprites;
import com.projectrebirth.client.ui.UiText;
import com.projectrebirth.technology.energy.EnergyPanelMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The window of the solar panel, the radioisotope generator, the battery and the backup battery (1.8.0 block B): a screen
 * with a picture of what the block does now and its main number, the energy column, the state in a word and a plate that
 * says why and what to do. No slots, so no inventory.
 */
public class EnergyPanelScreen extends UiMachineScreen<EnergyPanelMenu> {
    private static final int W = 280;
    private static final int H = 132;

    private final UiEnergy energy = new UiEnergy();

    public EnergyPanelScreen(EnergyPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H, "battery");
    }

    @Override
    protected String codexEntry() {
        return switch (menu.kind()) {
            case SOLAR -> "solar_panel";
            case ISOTOPE -> "radioisotope_generator";
            case BATTERY -> "battery";
            case BACKUP -> "backup_battery";
        };
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        energy.tick(menu.energy(), 0);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("screen.projectrebirth.energy_panel." + key, args);
    }

    /** The state in a word, its tone and the plate under it. */
    private record State(UiSprites.Tone tone, Component badge, Component plate) {
    }

    private State state() {
        int makes = menu.makes();
        return switch (menu.kind()) {
            case SOLAR -> switch (menu.state()) {
                case EnergyPanelMenu.SOLAR_SUN -> new State(UiSprites.Tone.OK, tr("making"), tr("solar.sun", makes));
                case EnergyPanelMenu.SOLAR_DUST -> new State(UiSprites.Tone.WARNING, tr("solar.dust_badge"), tr("solar.dust", makes));
                case EnergyPanelMenu.SOLAR_NIGHT -> new State(UiSprites.Tone.WARNING, tr("solar.night_badge"), tr("solar.night"));
                case EnergyPanelMenu.SOLAR_RAIN -> new State(UiSprites.Tone.WARNING, tr("solar.rain_badge"), tr("solar.rain"));
                default -> new State(UiSprites.Tone.ERROR, tr("solar.no_sky_badge"), tr("solar.no_sky"));
            };
            case ISOTOPE -> new State(UiSprites.Tone.OK, tr("making"), tr("isotope", makes));
            case BATTERY -> {
                int net = energy.net();
                if (net > 0) {
                    yield new State(UiSprites.Tone.OK, tr("battery.charging"), tr("battery.hint"));
                }
                if (net < 0) {
                    yield new State(UiSprites.Tone.WARNING, tr("battery.giving"), tr("battery.hint"));
                }
                yield new State(UiSprites.Tone.INFO, tr("battery.idle"), tr("battery.hint"));
            }
            case BACKUP -> menu.state() == EnergyPanelMenu.BACKUP_FEEDING
                    ? new State(UiSprites.Tone.WARNING, tr("backup.feeding_badge"), tr("backup.feeding", menu.extra()))
                    : new State(UiSprites.Tone.OK, tr("backup.waiting_badge"), tr("backup.waiting", menu.extra()));
        };
    }

    @Override
    protected void drawBody(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        State state = state();
        // the picture and the main number
        int sx = x + 7;
        int sy = y + 25;
        UiPanels.screen(graphics, sx, sy, 150, 80);
        long time = UiSounds.animations() ? System.currentTimeMillis() : 0L;
        drawPicture(graphics, sx + 6, sy + 8, time);
        Component big;
        Component small;
        if (menu.kind() == EnergyPanelMenu.Kind.BATTERY || menu.kind() == EnergyPanelMenu.Kind.BACKUP) {
            int percent = menu.energyCapacity() <= 0 ? 0 : Math.round(100.0f * menu.energy() / menu.energyCapacity());
            big = Component.literal(percent + "%");
            small = tr("charged");
        } else {
            big = Component.literal("+" + menu.makes());
            small = tr("per_tick");
        }
        UiText.scaled(graphics, big, sx + 82, sy + 22, 2.0f, menu.makes() > 0 || menu.kind() == EnergyPanelMenu.Kind.BATTERY
                || menu.kind() == EnergyPanelMenu.Kind.BACKUP ? UiColors.AMBER : UiColors.AMBER_DIM);
        text(graphics, small, sx + 82, sy + 42, 64, UiColors.AMBER_DIM);
        if (menu.kind() == EnergyPanelMenu.Kind.BACKUP) {
            text(graphics, tr("backup.blocks", menu.extra()), sx + 82, sy + 54, 64, UiColors.SCREEN_TEXT);
        }

        // energy column and state
        int rx = x + 163;
        int rw = W - 7 - 163;
        UiPanels.label(graphics, Component.translatable("screen.projectrebirth.station.label.energy"), rx, y + 26, rw);
        energy.draw(this, graphics, rx, y + 36, rw, 50, menu.energy(), menu.energyCapacity());
        badge(graphics, state.tone, state.badge, rx, y + 91, rw);
        plate(graphics, state.tone == UiSprites.Tone.OK ? UiSprites.Tone.INFO : state.tone, state.plate, x + 7, y + 111, W - 14);
    }

    /** A picture of the block's work in the terminal's amber lines, 70 x 64. */
    private void drawPicture(GuiGraphics graphics, int x, int y, long time) {
        int amber = UiColors.AMBER;
        int dim = UiColors.withAlpha(UiColors.AMBER, 110);
        switch (menu.kind()) {
            case SOLAR -> {
                // the panel: a slanted grid
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 4; col++) {
                        int px = x + 6 + col * 15 + row * 3;
                        int py = y + 44 + row * 6;
                        graphics.fill(px, py, px + 13, py + 5, row == 0 ? amber : dim);
                    }
                }
                int state = menu.state();
                if (state == EnergyPanelMenu.SOLAR_SUN || state == EnergyPanelMenu.SOLAR_DUST) {
                    int cx = x + 36;
                    int cy = y + 16;
                    graphics.fill(cx - 5, cy - 5, cx + 6, cy + 6, amber);
                    float turn = (time % 4000L) / 4000.0f * Mth.TWO_PI;
                    for (int i = 0; i < 8; i++) {
                        float a = turn + i * Mth.TWO_PI / 8;
                        int rx = cx + Math.round(Mth.cos(a) * 11);
                        int ry = cy + Math.round(Mth.sin(a) * 11);
                        graphics.fill(rx, ry, rx + 2, ry + 2, UiColors.AMBER_LIGHT);
                    }
                    if (state == EnergyPanelMenu.SOLAR_DUST) {
                        for (int i = 0; i < 14; i++) {
                            int dx = x + (int) Math.floorMod(i * 37 + time / 60, 68);
                            int dy = y + 4 + (i * 11) % 34;
                            graphics.fill(dx, dy, dx + 1, dy + 1, 0xFFC7A46A);
                        }
                    }
                } else if (state == EnergyPanelMenu.SOLAR_NIGHT) {
                    graphics.fill(x + 30, y + 8, x + 42, y + 22, dim);
                    graphics.fill(x + 34, y + 6, x + 46, y + 20, UiColors.SCREEN);
                } else if (state == EnergyPanelMenu.SOLAR_RAIN) {
                    for (int i = 0; i < 12; i++) {
                        int dx = x + 4 + i * 6;
                        int dy = y + 2 + (int) Math.floorMod(i * 13 + time / 40, 36);
                        graphics.fill(dx, dy, dx + 1, dy + 4, UiColors.ENERGY_TEXT);
                    }
                } else {
                    // a block over the panel, crossed out
                    graphics.fill(x + 22, y + 10, x + 50, y + 30, dim);
                    for (int i = 0; i < 20; i++) {
                        graphics.fill(x + 22 + i * 28 / 20, y + 10 + i, x + 24 + i * 28 / 20, y + 11 + i, UiColors.ERROR);
                    }
                }
            }
            case ISOTOPE -> {
                // a cylinder with a glowing core that pulses slowly
                graphics.fill(x + 20, y + 6, x + 52, y + 8, amber);
                graphics.fill(x + 20, y + 56, x + 52, y + 58, amber);
                graphics.fill(x + 20, y + 6, x + 22, y + 58, amber);
                graphics.fill(x + 50, y + 6, x + 52, y + 58, amber);
                float pulse = (Mth.sin(time / 600.0f) + 1.0f) * 0.5f;
                int glow = Math.round(6 + pulse * 4);
                graphics.fill(x + 36 - glow, y + 32 - glow, x + 36 + glow, y + 32 + glow, UiColors.withAlpha(0x7BD88F, 70));
                graphics.fill(x + 33, y + 29, x + 39, y + 35, UiColors.OK);
                for (int i = 0; i < 4; i++) {
                    graphics.fill(x + 24, y + 14 + i * 12, x + 48, y + 15 + i * 12, dim);
                }
            }
            case BATTERY, BACKUP -> {
                // the battery with its cells filled to the charge; a moving arrow shows charging or giving
                graphics.fill(x + 14, y + 4, x + 58, y + 6, amber);
                graphics.fill(x + 14, y + 58, x + 58, y + 60, amber);
                graphics.fill(x + 14, y + 4, x + 16, y + 60, amber);
                graphics.fill(x + 56, y + 4, x + 58, y + 60, amber);
                graphics.fill(x + 28, y + 1, x + 44, y + 4, amber);
                float charge = menu.energyCapacity() <= 0 ? 0.0f : menu.energy() / (float) menu.energyCapacity();
                int cells = Math.round(charge * 6);
                for (int i = 0; i < 6; i++) {
                    int cy = y + 51 - i * 8;
                    graphics.fill(x + 19, cy, x + 53, cy + 6, i < cells ? UiColors.ENERGY : UiColors.withAlpha(UiColors.ENERGY, 40));
                }
                if (menu.kind() == EnergyPanelMenu.Kind.BACKUP) {
                    boolean feeding = menu.state() == EnergyPanelMenu.BACKUP_FEEDING;
                    boolean lit = !feeding || UiSounds.reducedMotion() || (time / 400) % 2 == 0;
                    if (lit) {
                        int color = feeding ? UiColors.ERROR : UiColors.OK;
                        graphics.fill(x + 62, y + 26, x + 70, y + 30, color);
                        graphics.fill(x + 64, y + 24, x + 68, y + 32, color);
                    }
                }
                int net = energy.net();
                if (net != 0) {
                    int step = (int) ((time / 150) % 4);
                    int ax = x + 2;
                    int ay = net > 0 ? y + 40 - step * 6 : y + 22 + step * 6;
                    graphics.fill(ax, ay, ax + 7, ay + 2, net > 0 ? UiColors.OK : UiColors.WARNING);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
