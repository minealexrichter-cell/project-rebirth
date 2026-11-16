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

    /** The flash of the shot, at the flash bone (see MuzzleFlash). */
    private void drawFlash(PoseStack poseStack, WeaponView view, GeoBone bone, MultiBufferSource buffers) {
        poseStack.pushPose();
        software.bernie.geckolib.util.RenderUtil.prepMatrixForBone(poseStack, bone);
        software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(poseStack, bone);
        MuzzleFlash.draw(poseStack, buffers, view.flashAge(), view.flashSeed(), 1.0f);
        poseStack.popPose();
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
