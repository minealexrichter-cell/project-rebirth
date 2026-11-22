package com.projectrebirth.client;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The optical sight of a weapon with a scope (1.8.0): once the view is brought to the scope, the weapon and the armor's arms
 * are not drawn (WeaponClient), and the screen is the round lens: black around it, its edge darkening, a duplex reticle (thick
 * posts from the edge, thin lines in the middle), hold-over marks below the middle and a lit point of aim. Drawn in real screen
 * pixels, so the lines stay thin at any GUI scale. Client only.
 */
public final class ScopeOverlay {
    /** From this much of the zoom on, the eye is at the scope. */
    private static final float AT_SCOPE = 0.75f;
    private static final float LENS = 0.46f;
    private static final float[] EDGE_RADII = {0.985f, 0.96f, 0.93f, 0.89f};
    private static final int[] EDGE_ALPHAS = {0xE0, 0xA0, 0x60, 0x30};
    private static final int RETICLE = 0xFF050505;
    private static final int POINT = 0xFFFF4A2E;

    private static com.projectrebirth.client.ui.UiQuads quads;

    private ScopeOverlay() {
    }

    private static void quad(int x0, int y0, int x1, int y1, int color) {
        quads.fill(x0, y0, x1, y1, color);
    }

    /** Whether the eye is at the scope now: the weapon and the arms are not drawn, the lens is. */
    public static boolean lookingThrough(float partialTick) {
        return WeaponClient.zoomAmount(partialTick) >= AT_SCOPE;
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !lookingThrough(delta.getGameTimeDeltaPartialTick(false))) {
            return;
        }
        Window window = minecraft.getWindow();
        int w = window.getWidth(), h = window.getHeight();
        float scale = (float) window.getGuiScale();
        graphics.pose().pushPose();
        graphics.pose().scale(1.0f / scale, 1.0f / scale, 1.0f);
        // every rectangle into one buffer, drawn at once: one by one they cost a draw call each (1.8.0: 15 FPS)
        quads = com.projectrebirth.client.ui.UiQuads.begin(graphics);
        int cx = w / 2, cy = h / 2;
        int r = Math.round(Math.min(w, h) * LENS);
        // black above and below the lens
        quad(0, 0, w, cy - r, 0xFF000000);
        quad(0, cy + r, w, h, 0xFF000000);
        for (int dy = -r; dy < r; dy++) {
            int y = cy + dy;
            float mid = dy + 0.5f;
            int chord = Math.round(Mth.sqrt(Math.max(0.0f, r * r - mid * mid)));
            quad(0, y, cx - chord, y + 1, 0xFF000000);
            quad(cx + chord, y, w, y + 1, 0xFF000000);
            // the edge of the lens darkens towards the tube
            int outer = chord;
            for (int i = 0; i < EDGE_RADII.length; i++) {
                float rr = r * EDGE_RADII[i];
                int inner = Math.abs(mid) < rr ? Math.round(Mth.sqrt(rr * rr - mid * mid)) : 0;
                if (inner < outer) {
                    int color = EDGE_ALPHAS[i] << 24;
                    quad(cx - outer, y, cx - inner, y + 1, color);
                    quad(cx + inner, y, cx + outer, y + 1, color);
                }
                outer = inner;
                if (inner == 0) {
                    break;
                }
            }
        }
        reticle(cx, cy, r);
        quads.close();
        quads = null;
        graphics.pose().popPose();
    }

    private static void reticle(int cx, int cy, int r) {
        int thin = Math.max(1, Math.round(r / 260.0f));
        int thick = Math.max(3, Math.round(r / 55.0f));
        int post = Math.round(r * 0.32f);
        // thick posts from the edge, stopping short of the middle (no post from above, as on a hunting sight)
        quad(cx - r, cy - thick / 2, cx - post, cy - thick / 2 + thick, RETICLE);
        quad(cx + post, cy - thick / 2, cx + r, cy - thick / 2 + thick, RETICLE);
        quad(cx - thick / 2, cy + post, cx - thick / 2 + thick, cy + r, RETICLE);
        // thin lines through the middle
        quad(cx - post, cy - thin / 2, cx + post, cy - thin / 2 + thin, RETICLE);
        quad(cx - thin / 2, cy - r, cx - thin / 2 + thin, cy + post, RETICLE);
        // hold-over marks below the middle, for far targets
        for (int i = 1; i <= 3; i++) {
            int y = cy + Math.round(post * i / 4.0f);
            int half = Math.round(r * (0.05f - i * 0.008f));
            quad(cx - half, y - thin / 2, cx + half, y - thin / 2 + thin, RETICLE);
        }
        // side marks on the thin horizontal line
        for (int i = 1; i <= 3; i++) {
            int x = Math.round(post * i / 4.0f);
            int half = Math.max(2, Math.round(r * 0.015f));
            quad(cx - x - thin / 2, cy - half, cx - x - thin / 2 + thin, cy + half, RETICLE);
            quad(cx + x - thin / 2, cy - half, cx + x - thin / 2 + thin, cy + half, RETICLE);
        }
        // the lit point of aim
        int dot = Math.max(2, thin + 1);
        quad(cx - dot / 2, cy - dot / 2, cx - dot / 2 + dot, cy - dot / 2 + dot, POINT);
    }
}
