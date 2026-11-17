package com.projectrebirth.client.render;

import java.util.Optional;
import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projectrebirth.client.HudLayout;
import com.projectrebirth.config.RebirthClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;

/**
 * The two integrity bars over the player's own machines nearby (1.7.0, block I; the concept's section 5.1): structural on top,
 * green to red as it falls, critical below in orange. They face the camera like a name tag. HUD settings (key H): on or off,
 * and how near a machine must be.
 */
public final class MachineBars {
    private static final float WIDTH = 30.0f;

    private MachineBars() {
    }

    /** Draws the bars over the entity's head if it is the player's own machine and near enough. */
    public static void draw(Entity machine, Optional<UUID> owner, float structural, float maxStructural, float critical, float maxCritical,
                            PoseStack pose, MultiBufferSource buffers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!HudLayout.enabled() || !RebirthClientConfig.HUD_MACHINE_BARS.get() || minecraft.player == null || machine.isInvisible()
                || owner.filter(minecraft.player.getUUID()::equals).isEmpty() || maxStructural <= 0.0f || maxCritical <= 0.0f) {
            return;
        }
        double distance = RebirthClientConfig.HUD_MACHINE_BAR_DISTANCE.get();
        if (minecraft.player.distanceToSqr(machine) > distance * distance) {
            return;
        }
        float s = Math.max(0.0f, Math.min(1.0f, structural / maxStructural));
        float c = Math.max(0.0f, Math.min(1.0f, critical / maxCritical));
        pose.pushPose();
        pose.translate(0.0, machine.getBbHeight() + 0.45, 0.0);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025f, -0.025f, 0.025f);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
        float x0 = -WIDTH / 2.0f;
        // the background lies a little behind the bars, so that sorting cannot put it over them
        quad(buffer, matrix, x0 - 1.0f, -1.0f, x0 + WIDTH + 1.0f, 6.0f, -0.5f, 0xB0101010);
        int green = (int) (255 * s), red = (int) (255 * (1.0f - s));
        quad(buffer, matrix, x0, 0.0f, x0 + WIDTH * s, 3.0f, 0.5f, 0xFF000000 | Math.min(255, red + 60) << 16 | Math.min(255, green + 40) << 8 | 0x30);
        quad(buffer, matrix, x0, 4.0f, x0 + WIDTH * c, 5.0f, 0.5f, 0xFFFF9A2E);
        pose.popPose();
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int argb) {
        if (x1 <= x0) {
            return;
        }
        int light = LightTexture.FULL_BRIGHT;
        buffer.addVertex(matrix, x0, y0, z).setColor(argb).setLight(light);
        buffer.addVertex(matrix, x0, y1, z).setColor(argb).setLight(light);
        buffer.addVertex(matrix, x1, y1, z).setColor(argb).setLight(light);
        buffer.addVertex(matrix, x1, y0, z).setColor(argb).setLight(light);
    }
}
