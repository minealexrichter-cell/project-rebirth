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
        data.guard().ifPresent(pos -> box(pose, lines, camera, new AABB(pos).deflate(0.1), 0.3f, 0.75f, 1.0f));
        BlockPos previous = null;
        for (BlockPos point : data.route()) {
            box(pose, lines, camera, new AABB(point.above()).deflate(0.3), 1.0f, 0.85f, 0.2f);
            if (previous != null) {
                line(pose, lines, camera, Vec3.atCenterOf(previous.above()), Vec3.atCenterOf(point.above()));
            }
            previous = point;
        }
        data.areaFrom().ifPresent(from -> {
            BlockPos to = data.areaTo().orElse(from);
            AABB area = new AABB(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()),
                    Math.max(from.getX(), to.getX()) + 1, Math.max(from.getY(), to.getY()) + 1, Math.max(from.getZ(), to.getZ()) + 1);
            box(pose, lines, camera, area, 0.35f, 0.95f, 0.35f);
        });
        data.chest().ifPresent(pos -> box(pose, lines, camera, new AABB(pos).inflate(0.02), 1.0f, 0.55f, 0.1f));
        buffers.endBatch(RenderType.lines());
    }

    private static void box(PoseStack pose, VertexConsumer lines, Vec3 camera, AABB box, float r, float g, float b) {
        LevelRenderer.renderLineBox(pose, lines, box.move(-camera.x, -camera.y, -camera.z), r, g, b, 1.0f);
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
