package com.projectrebirth.client.multiblock;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.multiblock.MultiblockControllerBlock;
import com.projectrebirth.multiblock.MultiblockControllerBlockEntity;
import com.projectrebirth.multiblock.MultiblockSchema;
import com.projectrebirth.multiblock.MultiblockStatusPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The multiblocks on the client (1.7.0, block A): the list of errors a controller's window shows, and the hologram of a
 * construction: the missing blocks as see-through ghosts in place, the wrong ones and an occupied assembly zone framed in red.
 * Only the player who asked sees it; the same click switches it off, and it goes by itself after a while. Client only.
 */
public final class MultiblockClient {
    /** A hologram: until when it shows, and the last look at the construction (taken again every few ticks). */
    private static final class Hologram {
        long until;
        // far enough back that the first frame looks (Long.MIN_VALUE would overflow in now - lookedAt)
        long lookedAt = -1000L;
        List<MultiblockSchema.Problem> problems = List.of();
    }

    private static final Map<BlockPos, Hologram> HOLOGRAMS = new HashMap<>();
    @Nullable
    private static MultiblockStatusPayload status;

    private MultiblockClient() {
    }

    public static void register() {
        MultiblockStatusPayload.clientHandler = payload -> Minecraft.getInstance().execute(() -> status = payload);
        MultiblockControllerBlock.hologramToggle = MultiblockClient::toggle;
        NeoForge.EVENT_BUS.addListener(MultiblockClient::onRenderLevel);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            HOLOGRAMS.clear();
            status = null;
        });
    }

    /** The last list of errors of the controller at that place, or null. */
    @Nullable
    public static MultiblockStatusPayload status(BlockPos pos) {
        return status != null && status.pos().equals(pos) ? status : null;
    }

    private static long now() {
        return Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
    }

    private static void toggle(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (HOLOGRAMS.remove(pos) != null) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("message.projectrebirth.multiblock.hologram_off"), true);
            }
            return;
        }
        int seconds = RebirthConfig.SERVER_SPEC.isLoaded() ? RebirthConfig.MULTIBLOCK_HOLOGRAM_SECONDS.get() : 60;
        Hologram hologram = new Hologram();
        hologram.until = now() + seconds * 20L;
        HOLOGRAMS.put(pos.immutable(), hologram);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("message.projectrebirth.multiblock.hologram_on", seconds), true);
        }
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || HOLOGRAMS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        long now = now();
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Iterator<Map.Entry<BlockPos, Hologram>> it = HOLOGRAMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Hologram> entry = it.next();
            Hologram hologram = entry.getValue();
            if (now > hologram.until || !(minecraft.level.getBlockEntity(entry.getKey()) instanceof MultiblockControllerBlockEntity controller)) {
                it.remove();
                continue;
            }
            MultiblockSchema schema = controller.schema();
            if (schema == null) {
                continue;
            }
            if (now - hologram.lookedAt >= 5) {
                hologram.problems = schema.check(minecraft.level, entry.getKey(), controller.facing(), null).problems();
                hologram.lookedAt = now;
            }
            drawProblems(pose, camera, buffers, hologram.problems);
        }
    }

    private static void drawProblems(PoseStack pose, Vec3 camera, MultiBufferSource.BufferSource buffers, List<MultiblockSchema.Problem> problems) {
        Minecraft minecraft = Minecraft.getInstance();
        VertexConsumer translucent = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        MultiBufferSource ghost = type -> new GhostConsumer(translucent, 0.45f);
        for (MultiblockSchema.Problem problem : problems) {
            if (problem.kind() != MultiblockSchema.ProblemKind.MISSING || problem.expected().isAir()) {
                continue;
            }
            BlockPos pos = problem.pos();
            pose.pushPose();
            pose.translate(pos.getX() - camera.x + 0.5, pos.getY() - camera.y + 0.5, pos.getZ() - camera.z + 0.5);
            pose.scale(0.9f, 0.9f, 0.9f);
            pose.translate(-0.5, -0.5, -0.5);
            minecraft.getBlockRenderer().renderSingleBlock(problem.expected(), pose, ghost, 0xF000F0, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
        buffers.endBatch(Sheets.translucentCullBlockSheet());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (MultiblockSchema.Problem problem : problems) {
            boolean red = problem.kind() != MultiblockSchema.ProblemKind.MISSING;
            AABB box = new AABB(problem.pos()).inflate(red ? 0.01 : -0.04).move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(pose, lines, box, red ? 1.0f : 0.4f, red ? 0.2f : 0.85f, red ? 0.2f : 1.0f, 1.0f);
        }
        buffers.endBatch(RenderType.lines());
    }

    /** Whether a hologram shows at that place (for tests and the window). */
    public static boolean showing(BlockPos pos) {
        return HOLOGRAMS.containsKey(pos);
    }
}
