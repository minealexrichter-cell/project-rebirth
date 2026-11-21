package com.projectrebirth.client.screen;

import javax.annotation.Nullable;

import com.projectrebirth.client.ui.UiColors;
import com.projectrebirth.client.ui.UiSounds;
import com.projectrebirth.technology.Station;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The picture of the process on a station's screen (1.8.0 block B), one for each station, drawn in the amber lines of the
 * terminal with the product itself in the middle: a board under a soldering iron, a part between manipulators on a rail,
 * a plate under a press, a chip under a needle arm, a module on a turntable under a welding arc, a gun in the clamps of a
 * bench under a drill. It moves only while the station works (and the [ui] animations are on). Area: 64 x 52.
 */
final class StationArt {
    static final int WIDTH = 64;
    static final int HEIGHT = 52;
    private static final int AMBER = UiColors.AMBER;
    private static final int DIM = UiColors.withAlpha(UiColors.AMBER, 110);

    private StationArt() {
    }

    static void draw(GuiGraphics graphics, Station station, int x, int y, @Nullable ItemStack product, boolean running, long time) {
        boolean moving = running && UiSounds.animations();
        long t = moving ? time : 0L;
        switch (station) {
            case ELECTRONICS_FABRICATOR -> soldering(graphics, x, y, product, moving, t);
            case ARMOR_WORKSTATION -> press(graphics, x, y, product, moving, t);
            case IMPLANT_FABRICATOR -> needle(graphics, x, y, product, moving, t);
            case ARMOR_UPGRADE_FABRICATOR -> turntable(graphics, x, y, product, moving, t);
            case WEAPON_WORKSHOP -> bench(graphics, x, y, product, moving, t);
            default -> rail(graphics, x, y, product, moving, t);
        }
    }

    // ---------------------------------------------------------------- shared bits

    private static void item(GuiGraphics graphics, @Nullable ItemStack product, float x, float y) {
        if (product == null || product.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(2.0f, 2.0f, 1.0f);
        graphics.renderItem(product, 0, 0);
        graphics.pose().popPose();
    }

    /** Draws over the item (items are drawn high above the window). */
    private static void over(GuiGraphics graphics, Runnable drawing) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 250.0f);
        drawing.run();
        graphics.pose().popPose();
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void sparks(GuiGraphics graphics, int cx, int cy, int spread, long time, int color) {
        long step = time / 110;
        for (int i = 0; i < 4; i++) {
            long seed = step * 31 + i * 17;
            if (seed % 3 == 0) {
                continue;
            }
            int sx = cx - spread + (int) Math.floorMod(seed * 7, 2L * spread);
            int sy = cy - spread / 2 + (int) Math.floorMod(seed * 13, (long) spread);
            graphics.fill(sx, sy, sx + 1, sy + 1, color);
        }
    }

    // ---------------------------------------------------------------- the six pictures

    /** Assembly station: the part on a moving rail between two manipulators that reach in and out. */
    private static void rail(GuiGraphics graphics, int x, int y, @Nullable ItemStack product, boolean moving, long t) {
        int cx = x + WIDTH / 2;
        frame(graphics, x + 2, y + 44, WIDTH - 2, 7, AMBER);
        float phase = (t % 1200L) / 1200.0f;
        for (int i = 0; i < (WIDTH - 6) / 8; i++) {
            int tx = x + 4 + Math.floorMod(i * 8 + Math.round(phase * 8), WIDTH - 8);
            graphics.fill(tx, y + 46, tx + 3, y + 49, DIM);
        }
        if (product == null) {
            return;
        }
        int reach = moving ? Math.round(3 + 3 * Mth.sin(t / 220.0f)) : 0;
        for (int dy = 0; dy < 6 + reach; dy += 3) {
            graphics.fill(cx - 1, y + dy, cx + 1, y + dy + 2, AMBER);
        }
        for (int dx = 0; dx < 10 + reach; dx += 3) {
            graphics.fill(x + 2 + dx, y + 24, x + 4 + dx, y + 25, AMBER);
            graphics.fill(x + WIDTH - dx - 2, y + 24, x + WIDTH - dx, y + 25, AMBER);
        }
        item(graphics, product, cx - 16, y + 9 + (moving ? Mth.sin(t / 300.0f) * 1.5f : 0.0f));
        if (moving) {
            over(graphics, () -> sparks(graphics, cx, y + 22, 18, t, UiColors.SPARK));
        }
    }

    /** Electronics fabricator: a board with traces; the soldering iron runs along them and leaves a bright point. */
    private static void soldering(GuiGraphics graphics, int x, int y, @Nullable ItemStack product, boolean moving, long t) {
        int bx = x + 4;
        int by = y + 30;
        frame(graphics, bx, by, 56, 18, AMBER);
        for (int i = 0; i < 5; i++) {
            graphics.fill(bx + 5 + i * 10, by + 4, bx + 6 + i * 10, by + 14, DIM);
            graphics.fill(bx + 5 + i * 10, by + 8, bx + 11 + i * 10, by + 9, DIM);
        }
        if (product == null) {
            return;
        }
        item(graphics, product, x + 4, y + 2);
        float phase = (t % 2400L) / 2400.0f;
        int tipX = bx + 5 + Math.round(phase * 46);
        int tipY = by + 8;
        over(graphics, () -> {
            // the iron: a slanted handle and its tip
            for (int i = 0; i < 12; i++) {
                graphics.fill(tipX + i, tipY - 2 - i, tipX + i + 2, tipY - 1 - i, AMBER);
            }
            graphics.fill(tipX, tipY - 1, tipX + 1, tipY + 1, UiColors.AMBER_LIGHT);
            if (moving) {
                graphics.fill(tipX - 1, tipY, tipX + 2, tipY + 1, UiColors.SPARK);
                // a thin smoke going up
                int smoke = (int) ((t / 150) % 6);
                graphics.fill(tipX - 2, tipY - 4 - smoke, tipX - 1, tipY - 3 - smoke, DIM);
            }
        });
    }

    /** Armor workstation: a plate on an anvil, the press head comes down, a flash when it hits. */
    private static void press(GuiGraphics graphics, int x, int y, @Nullable ItemStack product, boolean moving, long t) {
        int cx = x + WIDTH / 2;
        // frame of the press
        graphics.fill(x + 6, y, x + 8, y + 50, AMBER);
        graphics.fill(x + WIDTH - 8, y, x + WIDTH - 6, y + 50, AMBER);
        graphics.fill(x + 6, y, x + WIDTH - 6, y + 2, AMBER);
        // anvil
        graphics.fill(cx - 18, y + 46, cx + 18, y + 50, AMBER);
        graphics.fill(cx - 10, y + 42, cx + 10, y + 46, DIM);
        if (product == null) {
            return;
        }
        item(graphics, product, cx - 16, y + 12);
        // the head goes down in 0.3 s, waits, goes up
        float cycle = (t % 1500L) / 1500.0f;
        float down = cycle < 0.2f ? cycle / 0.2f : cycle < 0.3f ? 1.0f : cycle < 0.7f ? 1.0f - (cycle - 0.3f) / 0.4f : 0.0f;
        int headY = y + 3 + Math.round(down * 6);
        over(graphics, () -> {
            graphics.fill(cx - 1, y + 2, cx + 1, headY, AMBER);
            graphics.fill(cx - 14, headY, cx + 14, headY + 5, AMBER);
            graphics.fill(cx - 12, headY + 1, cx + 12, headY + 4, UiColors.AMBER_BACK);
            if (moving && cycle >= 0.2f && cycle < 0.3f) {
                graphics.fill(cx - 20, y + 40, cx - 16, y + 41, UiColors.AMBER_LIGHT);
                graphics.fill(cx + 16, y + 40, cx + 20, y + 41, UiColors.AMBER_LIGHT);
                graphics.fill(cx - 18, y + 37, cx - 16, y + 38, UiColors.AMBER_LIGHT);
                graphics.fill(cx + 16, y + 37, cx + 18, y + 38, UiColors.AMBER_LIGHT);
            }
        });
    }

    /** Implant fabricator: the part under a fine arm; its needle goes down to the part and a short laser line blinks. */
    private static void needle(GuiGraphics graphics, int x, int y, @Nullable ItemStack product, boolean moving, long t) {
        int cx = x + WIDTH / 2;
        // stage with a scale
        graphics.fill(cx - 22, y + 48, cx + 22, y + 50, AMBER);
        for (int i = -20; i <= 20; i += 5) {
            graphics.fill(cx + i, y + 45, cx + i + 1, y + 48, DIM);
        }
        // the arm: a column on the left and a jointed arm over the stage
        graphics.fill(x + 4, y + 4, x + 6, y + 50, AMBER);
        if (product == null) {
            return;
        }
        item(graphics, product, cx - 12, y + 16);
        float swing = moving ? Mth.sin(t / 400.0f) : 0.0f;
        int jointX = x + 22 + Math.round(swing * 4);
        int tipX = cx + Math.round(swing * 8);
        float dip = moving ? (Mth.sin(t / 160.0f) + 1.0f) * 0.5f : 0.0f;
        int tipY = y + 14 + Math.round(dip * 6);
        over(graphics, () -> {
            graphics.fill(x + 5, y + 5, jointX, y + 7, AMBER);
            graphics.fill(jointX - 1, y + 4, jointX + 2, y + 8, UiColors.AMBER_LIGHT);
            for (int i = 0; i <= Math.abs(tipX - jointX); i++) {
                int px = jointX + Integer.signum(tipX - jointX) * i;
                int py = y + 7 + (tipY - y - 7) * i / Math.max(1, Math.abs(tipX - jointX));
                graphics.fill(px, py, px + 1, py + 1, AMBER);
            }
            graphics.fill(tipX, tipY, tipX + 1, tipY + 4, UiColors.AMBER_LIGHT);
            if (moving && (t / 200) % 3 == 0) {
                graphics.fill(tipX, tipY + 4, tipX + 1, y + 30, UiColors.SPARK);
            }
        });
    }

    /** Armor upgrade fabricator: the module turns slowly on a turntable while a welding arc flickers at its edge. */
    private static void turntable(GuiGraphics graphics, int x, int y, @Nullable ItemStack product, boolean moving, long t) {
        int cx = x + WIDTH / 2;
        graphics.fill(cx - 20, y + 44, cx + 20, y + 46, AMBER);
        graphics.fill(cx - 3, y + 46, cx + 3, y + 50, AMBER);
        graphics.fill(cx - 12, y + 50, cx + 12, y + 51, AMBER);
        // marks on the turntable's edge move to show the turning
        float phase = (t % 2000L) / 2000.0f;
        for (int i = 0; i < 5; i++) {
            int mx = cx - 18 + Math.floorMod(i * 8 + Math.round(phase * 8), 36);
            graphics.fill(mx, y + 43, mx + 2, y + 44, DIM);
        }
        if (product == null) {
            return;
        }
        item(graphics, product, cx - 16, y + 10);
        // the welder on the right
        graphics.fill(x + WIDTH - 6, y + 2, x + WIDTH - 4, y + 30, AMBER);
        graphics.fill(x + WIDTH - 16, y + 28, x + WIDTH - 4, y + 30, AMBER);
        if (moving) {
            over(graphics, () -> {
                if ((t / 90) % 4 != 0) {
                    int ax = x + WIDTH - 17;
                    graphics.fill(ax - 2, y + 30, ax - 1, y + 31, UiColors.SPARK);
                    graphics.fill(ax - 3, y + 31, ax - 2, y + 33, UiColors.SPARK);
                    graphics.fill(ax - 4, y + 33, ax - 3, y + 34, UiColors.AMBER_LIGHT);
                }
                sparks(graphics, x + WIDTH - 20, y + 34, 6, t, UiColors.AMBER_LIGHT);
            });
        }
    }

    /** Weapon workshop: the gun held by two clamps on the bench; a drill goes along it and shavings fall. */
    private static void bench(GuiGraphics graphics, int x, int y, @Nullable ItemStack product, boolean moving, long t) {
        // bench and legs
        graphics.fill(x + 2, y + 40, x + WIDTH, y + 43, AMBER);
        graphics.fill(x + 6, y + 43, x + 8, y + 51, AMBER);
        graphics.fill(x + WIDTH - 6, y + 43, x + WIDTH - 4, y + 51, AMBER);
        // clamps
        graphics.fill(x + 12, y + 30, x + 14, y + 40, DIM);
        graphics.fill(x + WIDTH - 14, y + 30, x + WIDTH - 12, y + 40, DIM);
        if (product == null) {
            return;
        }
        item(graphics, product, x + WIDTH / 2 - 16, y + 10);
        float phase = moving ? (Mth.sin(t / 700.0f) + 1.0f) * 0.5f : 0.5f;
        int drillX = x + 14 + Math.round(phase * (WIDTH - 30));
        over(graphics, () -> {
            graphics.fill(x + 2, y + 1, x + WIDTH, y + 3, AMBER);
            graphics.fill(drillX - 3, y + 3, drillX + 4, y + 8, AMBER);
            graphics.fill(drillX, y + 8, drillX + 1, y + 13, UiColors.AMBER_LIGHT);
            if (moving) {
                int fall = (int) ((t / 80) % 20);
                graphics.fill(drillX - 3, y + 16 + fall, drillX - 2, y + 17 + fall, DIM);
                graphics.fill(drillX + 3, y + 20 + fall / 2, drillX + 4, y + 21 + fall / 2, DIM);
            }
        });
    }
}
