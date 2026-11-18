package com.projectrebirth.client.ui;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The shared map of the mod's windows (console, monitoring station, weather station, shelter panel): a dark screen with
 * cells of chunks, layers, marks, zoom with the wheel, dragging with the left button and a tooltip of the cell under the
 * mouse. The cells are painted once into a texture (one pixel a chunk) and only repainted when the data or the layers
 * change ({@link #invalidate}); every frame only draws that texture scaled, the grid lines and the few marks.
 * The window must call {@link #close} when it closes (Screen.removed), to free the texture.
 */
public class UiMap extends AbstractWidget implements AutoCloseable {
    /** The data of the map: a square of chunks around an origin, coloured by the layers that are on. */
    public interface Source {
        /** Chunks per side of the square. */
        int side();

        /** The chunk at the square's top-left corner. */
        int minChunkX();

        int minChunkZ();

        /** The colour (0xAARRGGBB; alpha 0 = the empty ground of the map) of a chunk with the given layers on. */
        int color(int layers, int chunkX, int chunkZ);

        /** What the tooltip says about a chunk, or null. */
        @Nullable
        default Component describe(int layers, int chunkX, int chunkZ) {
            return null;
        }
    }

    /** A mark at a block position: a coloured square (or an icon) with its name in the tooltip. */
    public record Mark(double x, double z, int color, @Nullable ResourceLocation icon, Component label, int size) {
        public static Mark dot(double x, double z, int color, Component label) {
            return new Mark(x, z, color, null, label, 4);
        }
    }

    private static final int GROUND = 0xFF141A17;
    private static final int GRID = 0xFF1F2B23;
    private static final int MIN_CELL = 2;
    private static final int MAX_CELL = 16;
    private static int counter;

    private final Source source;
    private final Supplier<List<Mark>> marks;
    private final IntSupplier layers;
    private final ResourceLocation id = ProjectRebirth.id("ui_map/" + counter++);
    @Nullable
    private DynamicTexture texture;
    private int textureSide;
    private int paintedLayers = -1;
    private boolean dirty = true;
    private int cell = 6;
    /** The chunk (with fractions) at the centre of the view. */
    private double centreX;
    private double centreZ;
    private boolean dragged;

    public UiMap(int x, int y, int width, int height, Source source, IntSupplier layers, Supplier<List<Mark>> marks) {
        super(x, y, width, height, Component.empty());
        this.source = source;
        this.layers = layers;
        this.marks = marks;
        centreX = source.minChunkX() + source.side() / 2.0;
        centreZ = source.minChunkZ() + source.side() / 2.0;
    }

    /** The data changed: the cells are repainted before the next frame. */
    public void invalidate() {
        dirty = true;
    }

    public void centreOn(double blockX, double blockZ) {
        centreX = blockX / 16.0;
        centreZ = blockZ / 16.0;
    }

    public int cellSize() {
        return cell;
    }

    private void repaint() {
        int side = Math.max(1, source.side());
        if (texture == null || textureSide != side) {
            close();
            texture = new DynamicTexture(side, side, false);
            textureSide = side;
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        NativeImage image = texture.getPixels();
        int mask = layers.getAsInt();
        for (int dz = 0; dz < side; dz++) {
            for (int dx = 0; dx < side; dx++) {
                int argb = source.color(mask, source.minChunkX() + dx, source.minChunkZ() + dz);
                if ((argb >>> 24) == 0) {
                    argb = GROUND;
                }
                // NativeImage keeps ABGR
                int abgr = argb & 0xFF00FF00 | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16;
                image.setPixelRGBA(dx, dz, abgr);
            }
        }
        texture.upload();
        paintedLayers = mask;
        dirty = false;
    }

    private double left() {
        return getX() + width / 2.0 - centreX * cell;
    }

    private double top() {
        return getY() + height / 2.0 - centreZ * cell;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (dirty || paintedLayers != layers.getAsInt()) {
            repaint();
        }
        UiPanels.screen(graphics, getX(), getY(), width, height);
        int innerX = getX() + 2;
        int innerY = getY() + 2;
        graphics.enableScissor(innerX, innerY, getX() + width - 2, getY() + height - 2);
        graphics.fill(innerX, innerY, getX() + width - 2, getY() + height - 2, GROUND);
        int mapX = (int) Math.round(left() + source.minChunkX() * cell);
        int mapY = (int) Math.round(top() + source.minChunkZ() * cell);
        int size = textureSide * cell;
        graphics.blit(id, mapX, mapY, size, size, 0.0f, 0.0f, textureSide, textureSide, textureSide, textureSide);
        if (cell >= 5) {
            for (int line = 0; line <= textureSide; line++) {
                int lx = mapX + line * cell;
                if (lx >= innerX && lx < getX() + width - 2) {
                    graphics.fill(lx, Math.max(innerY, mapY), lx + 1, Math.min(getY() + height - 2, mapY + size), GRID);
                }
                int ly = mapY + line * cell;
                if (ly >= innerY && ly < getY() + height - 2) {
                    graphics.fill(Math.max(innerX, mapX), ly, Math.min(getX() + width - 2, mapX + size), ly + 1, GRID);
                }
            }
        }
        for (Mark mark : marks.get()) {
            int mx = (int) Math.round(left() + mark.x / 16.0 * cell);
            int my = (int) Math.round(top() + mark.z / 16.0 * cell);
            int half = mark.size / 2;
            if (mark.icon != null) {
                UiSprites.draw(graphics, mark.icon, mx - 3, my - 3, 7, 7);
            } else {
                graphics.fill(mx - half - 1, my - half - 1, mx + half + 1, my + half + 1, 0xFF000000);
                graphics.fill(mx - half, my - half, mx + half, my + half, mark.color);
            }
        }
        graphics.disableScissor();
    }

    /** What the tooltip of the map says under the mouse (a mark first, else the chunk), or null outside the map. */
    @Nullable
    public Component hovered(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return null;
        }
        for (Mark mark : marks.get()) {
            double mx = left() + mark.x / 16.0 * cell;
            double my = top() + mark.z / 16.0 * cell;
            if (Math.abs(mouseX - mx) <= Math.max(3, mark.size / 2 + 1) && Math.abs(mouseY - my) <= Math.max(3, mark.size / 2 + 1)) {
                return mark.label;
            }
        }
        int chunkX = Mth.floor((mouseX - left()) / cell);
        int chunkZ = Mth.floor((mouseY - top()) / cell);
        Component about = source.describe(layers.getAsInt(), chunkX, chunkZ);
        Component where = Component.translatable("screen.projectrebirth.ui.map.chunk", chunkX, chunkZ);
        return about == null ? where : where.copy().append(" · ").append(about);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        dragged = false;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        dragged = true;
        centreX -= dragX / cell;
        centreZ -= dragY / cell;
        clampCentre();
    }

    /** Whether the last press of the left button dragged the map (then it was not a click on a cell). */
    public boolean wasDragged() {
        return dragged;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        // zoom around the point under the mouse
        double chunkX = (mouseX - left()) / cell;
        double chunkZ = (mouseY - top()) / cell;
        int next = Mth.clamp(scrollY > 0 ? cell + Math.max(1, cell / 4) : cell - Math.max(1, cell / 5), MIN_CELL, MAX_CELL);
        if (next != cell) {
            cell = next;
            centreX = chunkX - (mouseX - getX() - width / 2.0) / cell;
            centreZ = chunkZ - (mouseY - getY() - height / 2.0) / cell;
            clampCentre();
        }
        return true;
    }

    private void clampCentre() {
        centreX = Mth.clamp(centreX, source.minChunkX(), source.minChunkX() + source.side());
        centreZ = Mth.clamp(centreZ, source.minChunkZ(), source.minChunkZ() + source.side());
    }

    @Override
    public void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
