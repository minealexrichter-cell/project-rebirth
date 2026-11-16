package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The muzzle flash, after the way gun mods such as TACZ draw it (1.6.0, parts B3d and B3f): a bright star facing forward and two
 * crossed tongues of flame along the barrel (the model's -Z), glowing by adding light, turned at random for each shot, big at the
 * moment of the shot and gone within two ticks. Shared by the pilot's weapons and the hostile machines. Client only.
 */
public final class MuzzleFlash {
    private static final ResourceLocation TEXTURE = ProjectRebirth.id("textures/effect/muzzle_flash.png");
    private static final float TICKS = 2.0f;
    /** When each machine last fired, by entity id (from the shot packets). */
    private static final Map<Integer, Long> LAST_SHOT = new HashMap<>();

    private MuzzleFlash() {
    }

    /** A machine fired (its shot packet arrived). */
    public static void onShot(int entityId, long gameTime) {
        LAST_SHOT.put(entityId, gameTime);
        if (LAST_SHOT.size() > 256) {
            LAST_SHOT.entrySet().removeIf(e -> gameTime - e.getValue() > 40L);
        }
    }

    /** 0 at the moment a machine fired, 1 when the flash is over; above 1 when there is none. */
    public static float age(int entityId, float partialTick) {
        Long at = LAST_SHOT.get(entityId);
        if (at == null || Minecraft.getInstance().level == null) {
            return 99.0f;
        }
        return (Minecraft.getInstance().level.getGameTime() + partialTick - at) / TICKS;
    }

    public static long seed(int entityId) {
        Long at = LAST_SHOT.get(entityId);
        return (at == null ? 0L : at) * 31L + entityId;
    }

    /** The pose at a bone's pivot, taken while the model is drawn, to draw the flash there after the model. */
    public static PoseStack.Pose capture(PoseStack poseStack, GeoBone bone) {
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);
        RenderUtil.translateToPivotPoint(poseStack, bone);
        PoseStack.Pose pose = poseStack.last().copy();
        poseStack.popPose();
        return pose;
    }

    /** Draws a flash at the pose; scale 1 is a rifle's flash. */
    public static void draw(PoseStack.Pose at, MultiBufferSource buffers, float age, long seed, float scale) {
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(at.pose());
        poseStack.last().normal().set(at.normal());
        draw(poseStack, buffers, age, seed, scale);
    }

    public static void draw(PoseStack poseStack, MultiBufferSource buffers, float age, long seed, float scale) {
        age = Math.max(0.0f, age);
        if (age >= 1.0f) {
            return;
        }
        Random random = new Random(seed);
        float size = scale * (0.9f + random.nextFloat() * 0.35f) * (1.0f - age * 0.55f);
        int c = (int) (255 * Math.max(0.0f, Math.min(1.0f, 1.0f - age * age)));
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TEXTURE));
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0f));
        float star = 0.55f * size;
        quad(vc, poseStack, -star, -star, -0.02f, star, -star, -0.02f, star, star, -0.02f, -star, star, -0.02f, 0.0f, 0.0f, 0.5f, 1.0f, c);
        poseStack.popPose();
        float length = 1.3f * size, width = 0.36f * size;
        float turn = random.nextFloat() * 90.0f;
        for (int i = 0; i < 2; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(turn + i * 90.0f));
            quad(vc, poseStack, 0.0f, -width, 0.0f, 0.0f, -width, -length, 0.0f, width, -length, 0.0f, width, 0.0f, 0.5f, 0.0f, 1.0f, 1.0f, c);
            poseStack.popPose();
        }
    }

    /** A quad seen from both sides; corners a b c d, the texture from (u0, v0) at a to (u1, v1) at c. */
    private static void quad(VertexConsumer vc, PoseStack poseStack, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float u0, float v0, float u1, float v1, int c) {
        PoseStack.Pose pose = poseStack.last();
        float[][] corners = {{ax, ay, az, u0, v0}, {bx, by, bz, u1, v0}, {cx, cy, cz, u1, v1}, {dx, dy, dz, u0, v1}};
        for (int side = 0; side < 2; side++) {
            for (int k = 0; k < 4; k++) {
                float[] p = corners[side == 0 ? k : 3 - k];
                vc.addVertex(pose, p[0], p[1], p[2]).setColor(c, c, c, 255).setUv(p[3], p[4])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(pose, 0.0f, 0.0f, side == 0 ? -1.0f : 1.0f);
            }
        }
    }
}
