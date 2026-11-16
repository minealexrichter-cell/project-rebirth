package com.projectrebirth.client.multiblock;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projectrebirth.multiblock.MultiblockSchema;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Draws the plan of a multiblock: as a turning 3D model up to a layer (the Codex), or one layer as a grid of icons (JEI). Client only. */
public final class MultiblockView {
    private MultiblockView() {
    }

    /**
     * The plan as a model on a turntable, the layers from the bottom up to {@code upTo} (1-based), centred there and fitted
     * into a square of {@code size} pixels.
     */
    public static void draw3d(GuiGraphics graphics, MultiblockSchema schema, int upTo, int centerX, int centerY, int size, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        int w = schema.width(), h = schema.height(), d = schema.depth();
        float scale = size / (float) Math.max(Math.max(w, d) * 1.45f, h * 1.2f);
        float angle = ((minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick) * 0.8f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 200);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(25));
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-w / 2.0f, -h / 2.0f, -d / 2.0f);
        Lighting.setupFor3DItems();
        var blocks = minecraft.getBlockRenderer();
        int layers = Math.min(h, Math.max(1, upTo));
        for (int y = 0; y < layers; y++) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    MultiblockSchema.Cell cell = schema.cell(y, z, x);
                    if (cell == null || cell.zone()) {
                        continue;
                    }
                    BlockState state = cell.display();
                    pose.pushPose();
                    pose.translate(x, y, z);
                    blocks.renderSingleBlock(state, pose, graphics.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            }
        }
        graphics.flush();
        pose.popPose();
    }

    /** One layer from above as a grid of icons, each {@code cell} pixels; the controller's side is at the bottom. */
    public static void drawLayer(GuiGraphics graphics, MultiblockSchema schema, int layer, int x, int y, int cell) {
        float scale = cell / 16.0f;
        for (int z = 0; z < schema.depth(); z++) {
            for (int col = 0; col < schema.width(); col++) {
                int px = x + col * cell, py = y + z * cell;
                graphics.fill(px, py, px + cell - 1, py + cell - 1, 0x22000000);
                MultiblockSchema.Cell found = schema.cell(layer, z, col);
                if (found == null) {
                    continue;
                }
                if (found.zone()) {
                    graphics.fill(px, py, px + cell - 1, py + cell - 1, 0x5537C6E8);
                    continue;
                }
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(px, py, 0);
                pose.scale(scale, scale, 1.0f);
                graphics.renderFakeItem(new ItemStack(found.display().getBlock()), 0, 0);
                pose.popPose();
            }
        }
    }
}
