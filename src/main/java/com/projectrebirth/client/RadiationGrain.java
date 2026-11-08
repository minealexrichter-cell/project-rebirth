package com.projectrebirth.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The look of a radiation burst: the view goes brownish, as through a dirty filter, and a fine film grain lies over it all
 * the time, both stronger with the burst. The grain is one tile of noise made once, laid over the screen at a new random
 * offset every frame, so that it crawls like the grain of an old film.
 */
final class RadiationGrain {
    private static final ResourceLocation TEXTURE = ProjectRebirth.id("radiation_grain");
    private static final int SIZE = 128;
    /** Screen pixels per grain: small enough to look like grain, big enough to be seen at any GUI scale. */
    private static final float GRAIN_PIXELS = 1.5f;
    private static final int TINT = 0x7A5226;
    private static final RandomSource RANDOM = RandomSource.create();
    private static boolean made;

    private RadiationGrain() {
    }

    /**
     * Makes the noise tile once: dark and light specks on a clear ground, each as opaque as it is far from grey, so that the grain
     * neither greys out nor darkens the picture.
     */
    private static void make() {
        NativeImage image = new NativeImage(SIZE, SIZE, false);
        RandomSource random = RandomSource.create(4242L);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double speck = random.nextGaussian();
                int alpha = Mth.clamp((int) (Math.abs(speck) * 70), 0, 255);
                int shade = speck > 0.0 ? 0xF2E6D2 : 0x140C04;
                // ABGR; the shades are warm and the same in all channels but a little, so the order hardly matters
                image.setPixelRGBA(x, y, alpha << 24 | (shade & 0xFF) << 16 | (shade & 0xFF00) | (shade >> 16 & 0xFF));
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
        // the tile repeats over the screen
        texture.bind();
        com.mojang.blaze3d.platform.GlStateManager._texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL11.GL_REPEAT);
        com.mojang.blaze3d.platform.GlStateManager._texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL11.GL_REPEAT);
        made = true;
    }

    /** @param strength 0 to 1: how strong the burst looks here */
    static void render(GuiGraphics graphics, float strength) {
        if (strength <= 0.0f) {
            return;
        }
        if (!made) {
            make();
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // the brownish filter, a little darker at the edges
        int tint = (int) (100 * strength);
        graphics.fill(0, 0, width, height, (tint << 24) | TINT);
        int edge = (int) (110 * strength);
        int band = height / 4;
        graphics.fillGradient(0, 0, width, band, (edge << 24) | 0x1A1208, 0x001A1208);
        graphics.fillGradient(0, height - band, width, height, 0x001A1208, (edge << 24) | 0x1A1208);
        // the sides too: a vignette, as in an old camera
        int side = width / 6;
        for (int i = 0; i < side; i += 2) {
            int a = (int) (edge * (1.0f - (float) i / side));
            graphics.fill(i, 0, i + 2, height, (a / 2 << 24) | 0x1A1208);
            graphics.fill(width - i - 2, 0, width - i, height, (a / 2 << 24) | 0x1A1208);
        }
        // the grain, at screen resolution rather than GUI units
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale() / GRAIN_PIXELS;
        int tilesWide = Mth.ceil(width * scale);
        int tilesHigh = Mth.ceil(height * scale);
        graphics.pose().pushPose();
        graphics.pose().scale(1.0f / scale, 1.0f / scale, 1.0f);
        RenderSystem.enableBlend();
        graphics.setColor(1.0f, 1.0f, 1.0f, Math.min(1.0f, 0.2f + 0.55f * strength));
        graphics.blit(TEXTURE, 0, 0, RANDOM.nextInt(SIZE), RANDOM.nextInt(SIZE), tilesWide, tilesHigh, SIZE, SIZE);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }
}
