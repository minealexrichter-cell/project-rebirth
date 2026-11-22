package com.projectrebirth.client.multiblock;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projectrebirth.multiblock.MultiblockSchema;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The hall drawn over a whole construction whose plan has a shell (1.7.0, the body workshop): instead of its open frames, thin
 * walls (2 pixels, like the transfer capsule's) on every outer side of the wall cells, the front ones of glass, a roof on top and
 * posts at the corners. Laid out in the plan's own frame (column, layer, row; the front is the last row) and turned with the
 * controller. Ports, the controller and the plates are left as they are. Client only.
 */
public final class HallShell {
    private static final double T = 2.0 / 16.0;
    private static final double POST_OUT = 0.5 / 16.0;
    private static final double POST = 3.0 / 16.0;

    /** A box of the hall in the plan's frame (cell units), its texture kind and the cell whose light it takes. */
    private record Box(double x0, double y0, double z0, double x1, double y1, double z1, int kind, int column, int layer, int row) {
    }

    private static final int WALL = 0, GLASS = 1, POST_KIND = 2;

    private HallShell() {
    }

    private static List<Box> boxes(MultiblockSchema schema, MultiblockSchema.Shell shell) {
        List<Box> out = new ArrayList<>();
        int w = schema.width(), d = schema.depth(), h = schema.height();
        for (int y = 0; y < h; y++) {
            for (int r = 0; r < d; r++) {
                for (int c = 0; c < w; c++) {
                    if (shell.walls().indexOf(schema.letter(y, r, c)) < 0) {
                        continue;
                    }
                    boolean left = c == 0, right = c == w - 1, back = r == 0, front = r == d - 1, top = y == h - 1;
                    double yTop = y + (top ? 1 - T : 1);
                    if (back) {
                        out.add(new Box(c, y, r, c + 1, yTop, r + T, WALL, c, y, r));
                    }
                    if (front) {
                        out.add(new Box(c, y, r + 1 - T, c + 1, yTop, r + 1, shell.glassFront() ? GLASS : WALL, c, y, r));
                    }
                    int side = shell.window(y, r) ? GLASS : WALL;
                    double z0 = r + (back ? T : 0), z1 = r + 1 - (front ? T : 0);
                    if (left) {
                        out.add(new Box(c, y, z0, c + T, yTop, z1, side, c, y, r));
                    }
                    if (right) {
                        out.add(new Box(c + 1 - T, y, z0, c + 1, yTop, z1, side, c, y, r));
                    }
                    if (top) {
                        out.add(new Box(c, y + 1 - T, r, c + 1, y + 1, r + 1, WALL, c, y, r));
                    }
                    if ((left || right) && (back || front)) {
                        double px0 = left ? c - POST_OUT : c + 1 - POST + POST_OUT;
                        double pz0 = back ? r - POST_OUT : r + 1 - POST + POST_OUT;
                        out.add(new Box(px0, y, pz0, px0 + POST, y + 1 + (top ? POST_OUT : 0), pz0 + POST, POST_KIND, c, y, r));
                    }
                }
            }
        }
        // pillars: a cell that must stand on something (a manipulator) gets a pillar down through the hidden cells under it, with
        // a wider cap under its foot; they stand a little off the wall panels so that no two faces meet in one plane
        for (int y = 1; y < h; y++) {
            for (int r = 0; r < d; r++) {
                for (int c = 0; c < w; c++) {
                    if (shell.supports().isEmpty() || shell.supports().indexOf(schema.letter(y, r, c)) < 0) {
                        continue;
                    }
                    int bottom = y;
                    while (bottom > 0 && shell.hide().indexOf(schema.letter(bottom - 1, r, c)) >= 0) {
                        bottom--;
                    }
                    if (bottom == y) {
                        continue;
                    }
                    double in = 2.5 / 16.0, cap = 1.5 / 16.0;
                    out.add(new Box(c + in, bottom, r + in, c + 1 - in, y - 1.0 / 16.0, r + 1 - in, POST_KIND, c, y - 1, r));
                    out.add(new Box(c + cap, y - 1.0 / 16.0, r + cap, c + 1 - cap, y, r + 1 - cap, POST_KIND, c, y - 1, r));
                }
            }
        }
        return out;
    }

    /** Draws the hall; the pose stands at the controller's block. */
    public static void render(MultiblockSchema schema, BlockPos controller, Direction facing, PoseStack pose, MultiBufferSource buffers) {
        MultiblockSchema.Shell shell = schema.shell().orElse(null);
        Level level = Minecraft.getInstance().level;
        if (shell == null || level == null) {
            return;
        }
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite[] sprites = {atlas.apply(shell.wall()), atlas.apply(shell.glass()), atlas.apply(shell.post())};
        VertexConsumer solid = buffers.getBuffer(Sheets.solidBlockSheet());
        List<Box> glass = new ArrayList<>();
        for (Box box : boxes(schema, shell)) {
            if (box.kind == GLASS) {
                glass.add(box);
            } else {
                draw(schema, controller, facing, pose, solid, sprites[box.kind], box, level);
            }
        }
        VertexConsumer clear = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        for (Box box : glass) {
            draw(schema, controller, facing, pose, clear, sprites[GLASS], box, level);
        }
    }

    /**
     * 1.8.0: a box given in the plan's own frame (cell units: column, layer, row), turned with the controller and drawn with that
     * texture and the light of the cell it stands in (the storage's manipulator). The pose stands at the controller's block.
     */
    public static void drawBox(MultiblockSchema schema, BlockPos controller, Direction facing, PoseStack pose, VertexConsumer out,
                               TextureAtlasSprite sprite, double x0, double y0, double z0, double x1, double y1, double z1) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            draw(schema, controller, facing, pose, out, sprite, new Box(x0, y0, z0, x1, y1, z1, WALL, (int) Math.floor((x0 + x1) / 2),
                    (int) Math.floor((y0 + y1) / 2), (int) Math.floor((z0 + z1) / 2)), level);
        }
    }

    private static void draw(MultiblockSchema schema, BlockPos controller, Direction facing, PoseStack pose, VertexConsumer out,
                             TextureAtlasSprite sprite, Box box, Level level) {
        Vec3 a = schema.worldPoint(controller, facing, box.x0, box.y0, box.z0);
        Vec3 b = schema.worldPoint(controller, facing, box.x1, box.y1, box.z1);
        double x0 = Math.min(a.x, b.x) - controller.getX(), x1 = Math.max(a.x, b.x) - controller.getX();
        double y0 = Math.min(a.y, b.y) - controller.getY(), y1 = Math.max(a.y, b.y) - controller.getY();
        double z0 = Math.min(a.z, b.z) - controller.getZ(), z1 = Math.max(a.z, b.z) - controller.getZ();
        BlockPos cell = BlockPos.containing(schema.worldPoint(controller, facing, box.column + 0.5, box.layer + 0.5, box.row + 0.5));
        int light = LevelRenderer.getLightColor(level, cell);
        Matrix4f m = pose.last().pose();
        PoseStack.Pose normal = pose.last();
        // each face takes the part of the texture its size covers in the cell, so thin edges show a strip, not the whole squeezed
        face(out, m, normal, sprite, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, x1 - x0, y1 - y0);
        face(out, m, normal, sprite, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, x1 - x0, y1 - y0);
        face(out, m, normal, sprite, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, z1 - z0, y1 - y0);
        face(out, m, normal, sprite, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, z1 - z0, y1 - y0);
        face(out, m, normal, sprite, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, x1 - x0, z1 - z0);
        face(out, m, normal, sprite, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, x1 - x0, z1 - z0);
    }

    /** One quad, corners counter-clockwise from the bottom left as seen from outside; the texture from its corner by size. */
    private static void face(VertexConsumer out, Matrix4f m, PoseStack.Pose normal, TextureAtlasSprite sprite, int light,
                             double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
                             double dx, double dy, double dz, float nx, float ny, float nz, double width, double height) {
        float u0 = sprite.getU0(), v1 = sprite.getV1();
        float u1 = sprite.getU((float) Math.min(1.0, width)), v0 = sprite.getV((float) (1.0 - Math.min(1.0, height)));
        vertex(out, m, normal, ax, ay, az, u0, v1, light, nx, ny, nz);
        vertex(out, m, normal, bx, by, bz, u1, v1, light, nx, ny, nz);
        vertex(out, m, normal, cx, cy, cz, u1, v0, light, nx, ny, nz);
        vertex(out, m, normal, dx, dy, dz, u0, v0, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, PoseStack.Pose normal, double x, double y, double z, float u, float v, int light,
                               float nx, float ny, float nz) {
        out.addVertex(m, (float) x, (float) y, (float) z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(normal, nx, ny, nz);
    }

}
