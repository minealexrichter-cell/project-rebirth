package com.projectrebirth.client.ui;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.8.0, block K: many textured rectangles of one texture in one draw call. The game draws every piece of a nine-sliced or
 * tiled sprite with a call of its own (GuiGraphics.blitSprite), and the mod's frames tile small pieces: a console window took
 * thousands of calls a frame and the human HUD panel a quarter of the frame. Open, add the pieces, {@link #end()}; nothing else
 * may be drawn in between (the game's tesselator is shared).
 */
public final class UiTexQuads {
    private final Matrix4f matrix;
    private final BufferBuilder buffer;

    private UiTexQuads(GuiGraphics graphics, ResourceLocation texture) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        this.matrix = new Matrix4f(graphics.pose().last().pose());
        this.buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
    }

    public static UiTexQuads begin(GuiGraphics graphics, ResourceLocation texture) {
        return new UiTexQuads(graphics, texture);
    }

    /** A rectangle from (x0, y0) to (x1, y1) with the texture coordinates (0 to 1) of its corners. */
    public void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        buffer.addVertex(matrix, x0, y0, 0.0f).setUv(u0, v0);
        buffer.addVertex(matrix, x0, y1, 0.0f).setUv(u0, v1);
        buffer.addVertex(matrix, x1, y1, 0.0f).setUv(u1, v1);
        buffer.addVertex(matrix, x1, y0, 0.0f).setUv(u1, v0);
    }

    /** Draws what was added (nothing when nothing was). */
    public void end() {
        @Nullable
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}
