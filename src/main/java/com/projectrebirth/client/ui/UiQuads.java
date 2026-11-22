package com.projectrebirth.client.ui;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

/**
 * Many filled rectangles drawn at once (1.8.0). Outside a screen every GuiGraphics.fill is a draw call of its own, so a HUD drawn
 * row by row (a lens, a slanted frame, scan lines) cost half the frame rate. Use in a try-with-resources block, with no other
 * drawing and no change of the pose inside it: the rectangles go into one buffer and are drawn when the block closes.
 */
public final class UiQuads implements AutoCloseable {
    private final GuiGraphics graphics;
    private final Matrix4f matrix;
    private final VertexConsumer buffer;

    private UiQuads(GuiGraphics graphics) {
        this.graphics = graphics;
        this.matrix = new Matrix4f(graphics.pose().last().pose());
        this.buffer = graphics.bufferSource().getBuffer(RenderType.gui());
    }

    public static UiQuads begin(GuiGraphics graphics) {
        return new UiQuads(graphics);
    }

    /** As GuiGraphics.fill (x0, y0 inclusive, x1, y1 exclusive), without its draw call. */
    public void fill(int x0, int y0, int x1, int y1, int color) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        buffer.addVertex(matrix, x0, y0, 0.0f).setColor(color);
        buffer.addVertex(matrix, x0, y1, 0.0f).setColor(color);
        buffer.addVertex(matrix, x1, y1, 0.0f).setColor(color);
        buffer.addVertex(matrix, x1, y0, 0.0f).setColor(color);
    }

    @Override
    public void close() {
        graphics.flush();
    }
}
