package com.projectrebirth.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projectrebirth.ProjectRebirth;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.RenderUtil;

/** Draws a weapon model with its grip at the current point of the pose. Rendering only. */
public final class WeaponRenderer extends GeoObjectRenderer<WeaponView> {
    public static final WeaponRenderer INSTANCE = new WeaponRenderer();
    private static final String MUZZLE = "muzzle";
    private static final String FLASH = "muzzle_flash";
    private static final String EJECTION = "ejection_port";

    /** How a drawing relates to the world, to learn where the muzzle is. */
    public enum Space {
        /** Relative to the camera, in world directions. */
        WORLD,
        /** Relative to the camera, in its own directions: a first-person hand. */
        VIEW,
        /** In a window, nothing to learn. */
        NONE
    }

    private Space space = Space.NONE;
    private final Matrix4f viewBase = new Matrix4f();

    private WeaponRenderer() {
        super(new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(WeaponView view) {
                return ProjectRebirth.id("geo/item/" + view.id() + ".geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(WeaponView view) {
                return ProjectRebirth.id("textures/item/weapon/" + view.id() + ".png");
            }

            @Override
            public ResourceLocation getAnimationResource(WeaponView view) {
                return ProjectRebirth.id("animations/item/" + view.id() + ".animation.json");
            }
        });
    }

    /** Call with the pose of the hand event, before anything is added to it. */
    public void beginHand(PoseStack poseStack) {
        viewBase.set(poseStack.last().pose());
    }

    public void draw(PoseStack poseStack, WeaponView view, MultiBufferSource buffers, int light, float partialTick, Space space) {
        this.space = space;
        poseStack.pushPose();
        // The renderer moves a model to the middle of a block; here the grip stays at the point given.
        poseStack.translate(-0.5f, -0.51f, -0.5f);
        render(poseStack, view, buffers, null, null, light, partialTick);
        poseStack.popPose();
    }

    @Override
    public void preRender(PoseStack poseStack, WeaponView view, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // the flash bone only marks the place; the flash itself is drawn as glowing planes (see drawFlash)
        getGeoModel().getBone(FLASH).ifPresent(bone -> bone.setHidden(true));
        super.preRender(poseStack, view, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, WeaponView view, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        String name = bone.getName();
        if (space != Space.NONE && (name.equals(MUZZLE) || name.equals(EJECTION))) {
            track(poseStack, view, bone, name.equals(MUZZLE));
        }
        if (name.equals(FLASH) && view.flashAge() < 1.0f) {
            drawFlash(poseStack, view, bone, bufferSource);
        }
        int light = packedLight;
        super.renderRecursively(poseStack, view, bone, renderType, bufferSource, buffer, isReRender, partialTick, light, packedOverlay, colour);
    }

    private static final ResourceLocation FLASH_TEXTURE = ProjectRebirth.id("textures/effect/muzzle_flash.png");

    /**
     * The muzzle flash, after the way gun mods such as TACZ draw it: a bright star facing forward and two crossed tongues of
     * flame along the barrel, glowing by adding light (black is see-through), turned at random for each shot, big at the moment
     * of the shot and shrinking away within two ticks.
     */
    private void drawFlash(PoseStack poseStack, WeaponView view, GeoBone bone, MultiBufferSource buffers) {
        float age = Math.max(0.0f, view.flashAge());
        java.util.Random random = new java.util.Random(view.flashSeed());
        float size = (0.9f + random.nextFloat() * 0.35f) * (1.0f - age * 0.55f);
        float bright = 1.0f - age * age;
        int c = (int) (255 * Math.max(0.0f, Math.min(1.0f, bright)));
        poseStack.pushPose();
        software.bernie.geckolib.util.RenderUtil.prepMatrixForBone(poseStack, bone);
        software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(poseStack, bone);
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(FLASH_TEXTURE));
        // the star, facing along the barrel (the weapon points to -Z), turned about the barrel at random
        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(random.nextFloat() * 360.0f));
        float star = 0.55f * size;
        quad(vc, poseStack, -star, -star, -0.02f, star, -star, -0.02f, star, star, -0.02f, -star, star, -0.02f, 0.0f, 0.0f, 0.5f, 1.0f, c);
        poseStack.popPose();
        // the tongues of flame, two crossed planes reaching forward
        float length = 1.3f * size, width = 0.36f * size;
        float turn = random.nextFloat() * 90.0f;
        for (int i = 0; i < 2; i++) {
            poseStack.pushPose();
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(turn + i * 90.0f));
            quad(vc, poseStack, 0.0f, -width, 0.0f, 0.0f, -width, -length, 0.0f, width, -length, 0.0f, width, 0.0f, 0.5f, 0.0f, 1.0f, 1.0f, c);
            poseStack.popPose();
        }
        poseStack.popPose();
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
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(pose, 0.0f, 0.0f, side == 0 ? -1.0f : 1.0f);
            }
        }
    }

    private void track(PoseStack poseStack, WeaponView view, GeoBone bone, boolean muzzle) {
        poseStack.pushPose();
        RenderUtil.translateToPivotPoint(poseStack, bone);
        Vector3f point = poseStack.last().pose().transformPosition(new Vector3f());
        poseStack.popPose();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 world = camera.getPosition();
        if (space == Space.VIEW) {
            // The game's own transform of a hand drawing is taken out, leaving the camera's own directions: right, up, and backwards along Z.
            point = new Matrix4f(viewBase).invert().transformPosition(point);
            Vector3f look = camera.getLookVector();
            Vector3f up = camera.getUpVector();
            Vector3f right = new Vector3f(look).cross(up);
            world = world.add(right.x * point.x + up.x * point.y - look.x * point.z,
                    right.y * point.x + up.y * point.y - look.y * point.z,
                    right.z * point.x + up.z * point.y - look.z * point.z);
        } else {
            world = world.add(point.x, point.y, point.z);
        }
        view.setTrackedPoints(muzzle ? world : null, muzzle ? null : world, view.now());
    }
}
