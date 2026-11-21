package com.projectrebirth.client.garrison;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projectrebirth.garrison.ConsoleMenu;
import com.projectrebirth.garrison.ConsoleNetwork;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.TabletData;
import com.projectrebirth.garrison.TabletItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The tablet's marks in the world while it is in hand (1.7.0, block D): the point to guard (blue), the patrol route (yellow, its
 * points joined), the work area (green) and its chest (orange). Also takes the console's views for the open window and the tablet's
 * screen. Client only.
 */
public final class TabletMarks {
    private TabletMarks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TabletMarks::render);
        TabletItem.openScreen = stack -> Minecraft.getInstance().setScreen(new TabletScreen(TabletItem.data(stack)));
        ConsoleNetwork.clientHandler = payload -> Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            TabletClient.accept(payload.console(), payload.view());
            if (minecraft.player != null && minecraft.player.containerMenu instanceof ConsoleMenu menu && menu.pos().equals(payload.console())) {
                menu.setView(payload.view());
                if (minecraft.screen instanceof ConsoleScreen screen) {
                    screen.viewChanged();
                }
            }
            if (minecraft.screen instanceof TabletScreen tablet && payload.console().equals(tablet.console())) {
                tablet.setView(payload.view());
            }
        });
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem().is(GarrisonRegistry.TABLET.get()) ? player.getMainHandItem()
                : player.getOffhandItem().is(GarrisonRegistry.TABLET.get()) ? player.getOffhandItem() : ItemStack.EMPTY;
        if (held.isEmpty()) {
            return;
        }
        TabletData data = TabletItem.data(held);
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        machineOrders(pose, lines, camera);
        if (data.pending().isPresent()) {
            // 1.7.1: while an order waits, only its points so far
            TabletData.Pending pending = data.pending().get();
            java.util.List<BlockPos> points = pending.points();
            if (pending.taskType() == com.projectrebirth.garrison.MachineTask.Type.PATROL) {
                route(pose, lines, camera, points);
            } else if (pending.taskType() == com.projectrebirth.garrison.MachineTask.Type.WORK && !points.isEmpty()) {
                area(pose, lines, camera, points.get(0), points.size() > 1 ? points.get(1) : points.get(0));
            }
            buffers.endBatch(RenderType.lines());
            acks(event, pose, camera, buffers);
            return;
        }
        data.guard().ifPresent(pos -> box(pose, lines, camera, new AABB(pos).deflate(0.1), 0.3f, 0.75f, 1.0f));
        route(pose, lines, camera, data.route());
        data.areaFrom().ifPresent(from -> area(pose, lines, camera, from, data.areaTo().orElse(from)));
        data.chest().ifPresent(pos -> box(pose, lines, camera, new AABB(pos).inflate(0.02), 1.0f, 0.55f, 0.1f));
        buffers.endBatch(RenderType.lines());
        acks(event, pose, camera, buffers);
    }

    /**
     * 1.8.0 block F: the orders of the console's machines in the world, from the console's view: routes as lines over the ground,
     * work areas as frames, guard points as small boxes, the circle and sectors of an area patrol. The chosen machines only, or all
     * of them when none is chosen.
     */
    private static void machineOrders(PoseStack pose, VertexConsumer lines, Vec3 camera) {
        com.projectrebirth.garrison.ConsoleView view = TabletClient.view();
        if (view == null) {
            return;
        }
        boolean any = !TabletClient.selection().isEmpty();
        for (com.projectrebirth.garrison.ConsoleView.Row row : view.rows()) {
            if (any && !TabletClient.selected(row.id())) {
                continue;
            }
            java.util.List<BlockPos> points = row.facts().points();
            if (points.isEmpty() || Vec3.atCenterOf(points.get(0)).distanceToSqr(camera) > 160 * 160) {
                continue;
            }
            switch (com.projectrebirth.garrison.MachineTask.Type.byId(row.task())) {
                case PATROL -> {
                    for (int i = 0; i < points.size(); i++) {
                        BlockPos a = points.get(i);
                        BlockPos b = points.get((i + 1) % points.size());
                        box(pose, lines, camera, new AABB(a.above()).deflate(0.35), 1.0f, 0.72f, 0.28f);
                        line(pose, lines, camera, Vec3.atBottomCenterOf(a.above()).add(0, 0.1, 0), Vec3.atBottomCenterOf(b.above()).add(0, 0.1, 0),
                                1.0f, 0.72f, 0.28f);
                    }
                }
                case GUARD -> box(pose, lines, camera, new AABB(points.get(0)).deflate(0.25), 0.31f, 0.76f, 1.0f);
                case WORK -> {
                    area(pose, lines, camera, points.get(0), points.get(1));
                    box(pose, lines, camera, new AABB(points.get(2)).inflate(0.02), 1.0f, 0.55f, 0.1f);
                }
                case AREA_PATROL -> {
                    BlockPos centre = points.get(0);
                    double radius = com.projectrebirth.config.RebirthConfig.SERVER_SPEC.isLoaded()
                            ? com.projectrebirth.config.RebirthConfig.AREA_PATROL_RADIUS.get() : 16;
                    Vec3 middle = Vec3.atBottomCenterOf(centre).add(0, 0.1, 0);
                    int segments = 48;
                    for (int i = 0; i < segments; i++) {
                        double a1 = Math.PI * 2 * i / segments, a2 = Math.PI * 2 * (i + 1) / segments;
                        line(pose, lines, camera, middle.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius),
                                middle.add(Math.cos(a2) * radius, 0, Math.sin(a2) * radius), 0.31f, 0.82f, 0.77f);
                    }
                    int sectors = Math.max(1, view.rows().stream().filter(other -> other.task() == row.task() && other.facts().points().equals(points)).toList().size());
                    for (int i = 0; i < sectors && sectors > 1; i++) {
                        double a = Math.PI * 2 * i / sectors;
                        line(pose, lines, camera, middle, middle.add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 0.31f, 0.82f, 0.77f);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** 1.8.0 block F: over each machine that just got an order, its name for two seconds (or a cross when it could not). */
    private static void acks(RenderLevelStageEvent event, PoseStack pose, Vec3 camera, MultiBufferSource.BufferSource buffers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            TabletClient.Ack ack = TabletClient.ack(entity.getUUID());
            if (ack == null) {
                continue;
            }
            Vec3 at = entity.getPosition(partial).add(0, entity.getBbHeight() + 0.7, 0);
            net.minecraft.network.chat.Component text = ack.taken()
                    ? net.minecraft.network.chat.Component.literal("✔ ").append(net.minecraft.network.chat.Component.translatable(
                            com.projectrebirth.garrison.MachineTask.Type.byId(ack.task()).translationKey()))
                    : net.minecraft.network.chat.Component.literal("✕");
            pose.pushPose();
            pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            pose.mulPose(event.getCamera().rotation());
            pose.scale(0.025f, -0.025f, 0.025f);
            net.minecraft.client.gui.Font font = minecraft.font;
            float x = -font.width(text) / 2.0f;
            int color = ack.taken() ? 0xFFB9F0C4 : 0xFFFF8A7A;
            font.drawInBatch(text, x, 0, color, false, pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                    0x80000000, 0xF000F0);
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static void route(PoseStack pose, VertexConsumer lines, Vec3 camera, java.util.List<BlockPos> points) {
        BlockPos previous = null;
        for (BlockPos point : points) {
            box(pose, lines, camera, new AABB(point.above()).deflate(0.3), 1.0f, 0.85f, 0.2f);
            if (previous != null) {
                line(pose, lines, camera, Vec3.atCenterOf(previous.above()), Vec3.atCenterOf(point.above()));
            }
            previous = point;
        }
    }

    private static void area(PoseStack pose, VertexConsumer lines, Vec3 camera, BlockPos from, BlockPos to) {
        AABB area = new AABB(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()),
                Math.max(from.getX(), to.getX()) + 1, Math.max(from.getY(), to.getY()) + 1, Math.max(from.getZ(), to.getZ()) + 1);
        box(pose, lines, camera, area, 0.35f, 0.95f, 0.35f);
    }

    private static void box(PoseStack pose, VertexConsumer lines, Vec3 camera, AABB box, float r, float g, float b) {
        LevelRenderer.renderLineBox(pose, lines, box.move(-camera.x, -camera.y, -camera.z), r, g, b, 1.0f);
    }

    private static void line(PoseStack pose, VertexConsumer lines, Vec3 camera, Vec3 a, Vec3 b, float r, float g, float bl) {
        Vec3 d = b.subtract(a).normalize();
        PoseStack.Pose last = pose.last();
        lines.addVertex(last, (float) (a.x - camera.x), (float) (a.y - camera.y), (float) (a.z - camera.z)).setColor(r, g, bl, 1.0f)
                .setNormal(last, (float) d.x, (float) d.y, (float) d.z);
        lines.addVertex(last, (float) (b.x - camera.x), (float) (b.y - camera.y), (float) (b.z - camera.z)).setColor(r, g, bl, 1.0f)
                .setNormal(last, (float) d.x, (float) d.y, (float) d.z);
    }

    private static void line(PoseStack pose, VertexConsumer lines, Vec3 camera, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a).normalize();
        PoseStack.Pose last = pose.last();
        lines.addVertex(last, (float) (a.x - camera.x), (float) (a.y - camera.y), (float) (a.z - camera.z)).setColor(1.0f, 0.85f, 0.2f, 1.0f)
                .setNormal(last, (float) d.x, (float) d.y, (float) d.z);
        lines.addVertex(last, (float) (b.x - camera.x), (float) (b.y - camera.y), (float) (b.z - camera.z)).setColor(1.0f, 0.85f, 0.2f, 1.0f)
                .setNormal(last, (float) d.x, (float) d.y, (float) d.z);
    }
}
