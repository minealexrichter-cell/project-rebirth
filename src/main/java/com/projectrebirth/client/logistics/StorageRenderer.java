package com.projectrebirth.client.logistics;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.client.multiblock.HallShell;
import com.projectrebirth.logistics.StorageControllerBlockEntity;
import com.projectrebirth.multiblock.MultiblockSchema;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 1.8.0: the look of a whole storage. Client only.
 * <ul>
 * <li>its hall (HallShell): corrugated walls with windows, a glass front; over it a smooth hip roof of carbon (its panels are
 * invisible while the storage is whole);</li>
 * <li>on the racks (3D shelves) a few of what lies in each one's share of the store, standing on the shelves facing the aisle;</li>
 * <li>a gantry manipulator under the roof over the aisle: a carriage runs along a rail, lowers its arm, reaches into a rack,
 * takes something, carries it to another rack and puts it there. Only a show: the store does not change; the moves come from the
 * game time, so every player sees the same.</li>
 * </ul>
 */
public class StorageRenderer implements BlockEntityRenderer<StorageControllerBlockEntity> {
    /** Ticks of one move of the manipulator (from a rack to a rack). */
    private static final int CYCLE = 160;
    /** Height of the rail under the roof, and of the arm when it travels (in cells of the plan). */
    private static final double RAIL = 1.9;
    private static final double TRAVEL = 1.55;
    private static final net.minecraft.resources.ResourceLocation BEAM = ProjectRebirth.id("block/crane_beam");
    private static final net.minecraft.resources.ResourceLocation CARRIAGE = ProjectRebirth.id("block/storage_controller_side");
    private static final net.minecraft.resources.ResourceLocation ARM = ProjectRebirth.id("block/workshop_post");

    /** A rack of the plan: its cell (column, layer, row) and which way its shelves face the aisle (+1: toward higher rows). */
    private record Rack(int column, int layer, int row, int toAisle) {
    }

    public StorageRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StorageControllerBlockEntity storage, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MultiblockSchema schema = storage.schema();
        Level level = Minecraft.getInstance().level;
        if (schema == null || level == null || !storage.formed()) {
            return;
        }
        BlockPos at = storage.getBlockPos();
        Direction facing = storage.facing();
        HallShell.render(schema, at, facing, pose, buffers);
        roof(schema, at, facing, pose, buffers, level);
        List<Rack> racks = racks(schema);
        List<ItemStack> shelves = storage.shelves();
        for (int k = 0; k < racks.size(); k++) {
            Rack rack = racks.get(k);
            for (int j = 0; j < StorageControllerBlockEntity.SHOWN_PER_RACK; j++) {
                int index = k * StorageControllerBlockEntity.SHOWN_PER_RACK + j;
                if (index >= shelves.size() || shelves.get(index).isEmpty()) {
                    continue;
                }
                // two on the lower shelf, two on the middle one, side by side along the aisle
                double x = rack.column() + (j % 2 == 0 ? 0.32 : 0.68);
                double y = rack.layer() + (j < 2 ? 2.0 / 16.0 : 9.0 / 16.0);
                double z = rack.row() + 0.5;
                drawItem(schema, at, facing, pose, buffers, level, shelves.get(index), x, y, z, rack.toAisle(), 0.36f);
            }
        }
        manipulator(schema, at, facing, pose, buffers, level, racks, shelves, level.getGameTime() + partialTick);
    }

    /** The racks of the plan in its order (the order the store gives them their shares in). */
    private static List<Rack> racks(MultiblockSchema schema) {
        List<Rack> out = new ArrayList<>();
        int aisle = -1;
        for (int r = 0; r < schema.depth() && aisle < 0; r++) {
            if (schema.letter(0, r, 0) == 'Z') {
                aisle = r;
            }
        }
        for (int y = 0; y < schema.height(); y++) {
            for (int r = 0; r < schema.depth(); r++) {
                for (int c = 0; c < schema.width(); c++) {
                    if (schema.letter(y, r, c) == 'R') {
                        out.add(new Rack(c, y, r, aisle < 0 || aisle > r ? 1 : -1));
                    }
                }
            }
        }
        return out;
    }

    /** An item standing at a point of the plan, its face turned toward the aisle. */
    private static void drawItem(MultiblockSchema schema, BlockPos at, Direction facing, PoseStack pose, MultiBufferSource buffers, Level level,
                                 ItemStack stack, double x, double y, double z, int toAisle, float scale) {
        Vec3 point = schema.worldPoint(at, facing, x, y, z);
        Vec3 ahead = schema.worldPoint(at, facing, x, y, z + toAisle).subtract(point);
        float yaw = (float) (Mth.atan2(ahead.x, ahead.z) * Mth.RAD_TO_DEG);
        pose.pushPose();
        pose.translate(point.x - at.getX(), point.y - at.getY() + scale * 0.5, point.z - at.getZ());
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.scale(scale, scale, scale);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(point.add(0, 0.3, 0)));
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
    }

    /** A number from the cycle, the same on every client. */
    private static int pick(long cycle, int salt, int bound) {
        long h = cycle * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        return (int) Math.floorMod(h, (long) bound);
    }

    /** The rack the manipulator takes from (salt 1) or puts into (salt 2) in that cycle. */
    private static int rackOf(long cycle, int salt, int racks) {
        int from = pick(cycle, 1, racks);
        if (salt == 1) {
            return from;
        }
        int to = pick(cycle, 2, racks - 1);
        return to >= from ? to + 1 : to;
    }

    private static double smooth(double t) {
        t = Mth.clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * The gantry over the aisle. One cycle: to the first rack along the rail, down and into it, takes, out and up, along to the
     * second rack, down and into it, puts, out and up.
     */
    private static void manipulator(MultiblockSchema schema, BlockPos at, Direction facing, PoseStack pose, MultiBufferSource buffers, Level level,
                                    List<Rack> racks, List<ItemStack> shelves, double time) {
        if (racks.size() < 2) {
            return;
        }
        int aisle = -1;
        for (int r = 0; r < schema.depth() && aisle < 0; r++) {
            if (schema.letter(0, r, 0) == 'Z') {
                aisle = r;
            }
        }
        if (aisle < 0) {
            return;
        }
        long cycle = (long) Math.floor(time / CYCLE);
        double f = (time - cycle * (double) CYCLE) / CYCLE;
        Rack from = racks.get(rackOf(cycle, 1, racks.size())), to = racks.get(rackOf(cycle, 2, racks.size()));
        Rack before = racks.get(rackOf(cycle - 1, 2, racks.size()));
        double fromShelf = from.layer() + (pick(cycle, 3, 2) == 0 ? 0.3 : 0.72);
        double toShelf = to.layer() + (pick(cycle, 4, 2) == 0 ? 0.3 : 0.72);
        double x, armY, reach;
        boolean carrying;
        Rack side;
        if (f < 0.2) {
            x = Mth.lerp(smooth(f / 0.2), before.column() + 0.5, from.column() + 0.5);
            armY = TRAVEL;
            reach = 0.0;
            carrying = false;
            side = from;
        } else if (f < 0.45) {
            double t = f < 0.3 ? smooth((f - 0.2) / 0.1) : f < 0.35 ? 1.0 : 1.0 - smooth((f - 0.35) / 0.1);
            x = from.column() + 0.5;
            armY = Mth.lerp(t, TRAVEL, fromShelf);
            reach = t;
            carrying = f >= 0.32;
            side = from;
        } else if (f < 0.7) {
            x = Mth.lerp(smooth((f - 0.45) / 0.25), from.column() + 0.5, to.column() + 0.5);
            armY = TRAVEL;
            reach = 0.0;
            carrying = true;
            side = to;
        } else {
            double t = f < 0.8 ? smooth((f - 0.7) / 0.1) : f < 0.85 ? 1.0 : 1.0 - smooth((f - 0.85) / 0.15);
            x = to.column() + 0.5;
            armY = Mth.lerp(t, TRAVEL, toShelf);
            reach = t;
            carrying = f < 0.82;
            side = to;
        }
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite beam = atlas.apply(BEAM), carriage = atlas.apply(CARRIAGE), arm = atlas.apply(ARM);
        VertexConsumer solid = buffers.getBuffer(Sheets.solidBlockSheet());
        double mid = aisle + 0.5;
        // the rail along the aisle, under the roof
        HallShell.drawBox(schema, at, facing, pose, solid, beam, 0.02, RAIL, mid - 0.07, schema.width() - 0.02, RAIL + 0.08, mid + 0.07);
        // the carriage on the rail
        HallShell.drawBox(schema, at, facing, pose, solid, carriage, x - 0.2, RAIL - 0.12, mid - 0.16, x + 0.2, RAIL, mid + 0.16);
        // the arm: down from the carriage, then into the rack toward its side
        HallShell.drawBox(schema, at, facing, pose, solid, arm, x - 0.04, armY, mid - 0.04, x + 0.04, RAIL - 0.12, mid + 0.04);
        double end = mid + side.toAisle() * -1 * (0.1 + reach * 0.62);
        double z0 = Math.min(mid, end), z1 = Math.max(mid, end);
        HallShell.drawBox(schema, at, facing, pose, solid, arm, x - 0.035, armY - 0.035, z0 - 0.035, x + 0.035, armY + 0.035, z1 + 0.035);
        // the gripper at the end: two jaws, open when it reaches with nothing, closed round what it carries
        double open = carrying ? 0.07 : 0.12;
        HallShell.drawBox(schema, at, facing, pose, solid, carriage, x - open - 0.02, armY - 0.13, end - 0.05, x - open + 0.02, armY + 0.02, end + 0.05);
        HallShell.drawBox(schema, at, facing, pose, solid, carriage, x + open - 0.02, armY - 0.13, end - 0.05, x + open + 0.02, armY + 0.02, end + 0.05);
        if (carrying) {
            int k = racks.indexOf(from);
            ItemStack shown = ItemStack.EMPTY;
            for (int j = 0; j < StorageControllerBlockEntity.SHOWN_PER_RACK && shown.isEmpty(); j++) {
                int index = k * StorageControllerBlockEntity.SHOWN_PER_RACK + (j + pick(cycle, 5, 4)) % StorageControllerBlockEntity.SHOWN_PER_RACK;
                if (index < shelves.size()) {
                    shown = shelves.get(index);
                }
            }
            if (!shown.isEmpty()) {
                drawItem(schema, at, facing, pose, buffers, level, shown, x, armY - 0.14, end, side.toAisle(), 0.28f);
            }
        }
    }

    // ------------------------------------------------------------------ the roof

    private static final net.minecraft.resources.ResourceLocation ROOF = ProjectRebirth.id("block/storage_roof");

    /** How far the eaves stand out past the walls, in cells (a pixel: over the corner posts of the hall, which stand out half). */
    private static final double EAVES = 1.0 / 16.0;

    /**
     * The height of the hip roof over a point of the plan, in cells: 0 at the eaves (a pixel out past the walls), rising at 45
     * degrees, 1 over the flat middle.
     */
    private static double roofHeight(double x, double z, int w, int d) {
        double in = Math.min(Math.min(x + EAVES, w + EAVES - x), Math.min(z + EAVES, d + EAVES - z));
        return Mth.clamp(in, 0.0, 1.0);
    }

    /** The lines of the roof's grid along one side: the eaves, then every cell edge, then the eaves again. */
    private static double[] grid(int size) {
        double[] out = new double[size + 3];
        out[0] = -EAVES;
        for (int i = 0; i <= size; i++) {
            out[i + 1] = i;
        }
        out[size + 2] = size + EAVES;
        return out;
    }

    /**
     * The smooth hip roof over the top layer of the plan (its panels are invisible while the storage is whole). Every square of
     * its grid (the cells and the thin strip of the eaves round them) is two triangles split along the diagonal through its
     * highest corner — so at the corners the triangles meet in a hip, along the sides they make one slope of 45 degrees, in the
     * middle a flat top — one texture tile a cell. Under it a flat ceiling, seen from inside and from under the eaves. It stands
     * a pixel off the walls' posts everywhere (no two faces in one plane).
     */
    private static void roof(MultiblockSchema schema, BlockPos at, Direction facing, PoseStack pose, MultiBufferSource buffers, Level level) {
        int w = schema.width(), d = schema.depth(), top = schema.height() - 1;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ROOF);
        VertexConsumer out = buffers.getBuffer(Sheets.solidBlockSheet());
        PoseStack.Pose last = pose.last();
        double[] xs = grid(w), zs = grid(d);
        for (int j = 0; j + 1 < zs.length; j++) {
            for (int i = 0; i + 1 < xs.length; i++) {
                double x0 = xs[i], x1 = xs[i + 1], z0 = zs[j], z1 = zs[j + 1];
                int c = Mth.clamp((int) Math.floor((x0 + x1) / 2), 0, w - 1), r = Mth.clamp((int) Math.floor((z0 + z1) / 2), 0, d - 1);
                BlockPos cell = BlockPos.containing(schema.worldPoint(at, facing, c + 0.5, top + 0.5, r + 0.5));
                int light = LevelRenderer.getLightColor(level, cell.above());
                double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
                double[] h = new double[4];
                int high = 0;
                for (int k = 0; k < 4; k++) {
                    h[k] = roofHeight(corners[k][0], corners[k][1], w, d);
                    if (h[k] > h[high]) {
                        high = k;
                    }
                }
                // the diagonal through the highest corner: where three corners lie low, the two triangles fold along the hip
                int a = high, b = (high + 2) % 4;
                for (int side : new int[] {1, 3}) {
                    triangle(schema, at, facing, out, last, sprite, light, top, corners, h, a, (a + side) % 4, b, x0, z0, x1 - x0, z1 - z0);
                }
                // the ceiling under it
                BlockPos below = BlockPos.containing(schema.worldPoint(at, facing, c + 0.5, top - 0.5, r + 0.5));
                int inside = LevelRenderer.getLightColor(level, below);
                Vec3 p0 = point(schema, at, facing, x0, top, z0), p1 = point(schema, at, facing, x1, top, z0),
                        p2 = point(schema, at, facing, x1, top, z1), p3 = point(schema, at, facing, x0, top, z1);
                float u1 = (float) Math.min(1.0, x1 - x0), v1 = (float) Math.min(1.0, z1 - z0);
                quad(out, last, sprite, inside, p0, 0, 0, p1, u1, 0, p2, u1, v1, p3, 0, v1, 0, -1, 0);
            }
        }
    }


    private static Vec3 point(MultiblockSchema schema, BlockPos at, Direction facing, double x, double y, double z) {
        return schema.worldPoint(at, facing, x, y, z).subtract(at.getX(), at.getY(), at.getZ());
    }

    /** One triangle of a roof cell, wound so that it faces up (as the up faces of blocks), drawn as a quad with a vertex twice. */
    private static void triangle(MultiblockSchema schema, BlockPos at, Direction facing, VertexConsumer out, PoseStack.Pose last,
                                 TextureAtlasSprite sprite, int light, int top, double[][] corners, double[] h, int i0, int i1, int i2,
                                 double x0, double z0, double sx, double sz) {
        // seen from above, the up face of a block runs (x0, z1), (x1, z1), (x1, z0): a negative turn in (x, z)
        double turn = (corners[i1][0] - corners[i0][0]) * (corners[i2][1] - corners[i0][1])
                - (corners[i1][1] - corners[i0][1]) * (corners[i2][0] - corners[i0][0]);
        if (turn > 0) {
            int swap = i1;
            i1 = i2;
            i2 = swap;
        }
        Vec3 p0 = point(schema, at, facing, corners[i0][0], top + h[i0], corners[i0][1]);
        Vec3 p1 = point(schema, at, facing, corners[i1][0], top + h[i1], corners[i1][1]);
        Vec3 p2 = point(schema, at, facing, corners[i2][0], top + h[i2], corners[i2][1]);
        Vec3 normal = p1.subtract(p0).cross(p2.subtract(p0)).normalize();
        if (normal.y < 0) {
            normal = normal.scale(-1);
        }
        float u0 = uv(corners[i0][0] - x0, sx), w0 = uv(corners[i0][1] - z0, sz), u1 = uv(corners[i1][0] - x0, sx), w1 = uv(corners[i1][1] - z0, sz),
                u2 = uv(corners[i2][0] - x0, sx), w2 = uv(corners[i2][1] - z0, sz);
        quad(out, last, sprite, light, p0, u0, w0, p1, u1, w1, p2, u2, w2, p2, u2, w2, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    /** A corner of a square of the grid on the tile: a whole cell takes the whole tile, a thin strip only as much as it is wide. */
    private static float uv(double offset, double size) {
        return (float) Math.min(1.0, offset >= size - 1.0e-6 ? Math.min(1.0, size) : offset);
    }

    private static void quad(VertexConsumer out, PoseStack.Pose last, TextureAtlasSprite sprite, int light, Vec3 a, float au, float av, Vec3 b,
                             float bu, float bv, Vec3 c, float cu, float cv, Vec3 d, float du, float dv, float nx, float ny, float nz) {
        vertex(out, last, sprite, light, a, au, av, nx, ny, nz);
        vertex(out, last, sprite, light, b, bu, bv, nx, ny, nz);
        vertex(out, last, sprite, light, c, cu, cv, nx, ny, nz);
        vertex(out, last, sprite, light, d, du, dv, nx, ny, nz);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose last, TextureAtlasSprite sprite, int light, Vec3 p, float u, float v,
                               float nx, float ny, float nz) {
        out.addVertex(last.pose(), (float) p.x, (float) p.y, (float) p.z).setColor(255, 255, 255, 255)
                .setUv(sprite.getU(u), sprite.getV(v)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, nx, ny, nz);
    }

    @Override
    public AABB getRenderBoundingBox(StorageControllerBlockEntity storage) {
        return storage.renderBounds();
    }

    @Override
    public boolean shouldRenderOffScreen(StorageControllerBlockEntity storage) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
