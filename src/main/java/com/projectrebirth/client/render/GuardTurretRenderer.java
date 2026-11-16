package com.projectrebirth.client.render;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.hostile.GuardTurretEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The guard turret: its head turns to where it looks (bone "head"), its barrel tilts (bone "barrel"). */
public class GuardTurretRenderer extends GeoEntityRenderer<GuardTurretEntity> {
    public GuardTurretRenderer(EntityRendererProvider.Context context) {
        super(context, new TurretModel());
        this.shadowRadius = 0.6f;
    }

    private final java.util.List<com.mojang.blaze3d.vertex.PoseStack.Pose> flashes = new java.util.ArrayList<>();

    /** The flash of its shot, drawn at the muzzle bone after the model (see MuzzleFlash). */
    @Override
    public void render(GuardTurretEntity turret, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        flashes.clear();
        super.render(turret, entityYaw, partialTick, poseStack, buffers, light);
        for (com.mojang.blaze3d.vertex.PoseStack.Pose pose : flashes) {
            MuzzleFlash.draw(pose, buffers, MuzzleFlash.age(turret.getId(), partialTick), MuzzleFlash.seed(turret.getId()), 0.6f);
        }
        flashes.clear();
    }

    @Override
    public void renderRecursively(com.mojang.blaze3d.vertex.PoseStack poseStack, GuardTurretEntity turret, GeoBone bone, net.minecraft.client.renderer.RenderType renderType,
                                  net.minecraft.client.renderer.MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender && bone.getName().equals("muzzle") && MuzzleFlash.age(turret.getId(), partialTick) < 1.0f) {
            flashes.add(MuzzleFlash.capture(poseStack, bone));
        }
        super.renderRecursively(poseStack, turret, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private static class TurretModel extends GeoModel<GuardTurretEntity> {
        @Override
        public ResourceLocation getModelResource(GuardTurretEntity turret) {
            return ProjectRebirth.id("geo/entity/guard_turret.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(GuardTurretEntity turret) {
            return ProjectRebirth.id("textures/entity/guard_turret.png");
        }

        @Override
        public ResourceLocation getAnimationResource(GuardTurretEntity turret) {
            return ProjectRebirth.id("animations/entity/guard_turret.animation.json");
        }

        @Override
        public void setCustomAnimations(GuardTurretEntity turret, long instanceId, AnimationState<GuardTurretEntity> state) {
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            GeoBone head = getAnimationProcessor().getBone("head");
            GeoBone barrel = getAnimationProcessor().getBone("barrel");
            if (data != null && head != null) {
                head.setRotY(data.netHeadYaw() * Mth.DEG_TO_RAD);
            }
            if (data != null && barrel != null) {
                barrel.setRotX(data.headPitch() * Mth.DEG_TO_RAD);
            }
        }
    }
}
