package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.hostile.MechRocketEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** A rocket of the mech: a small GeckoLib model, its nose turned along its flight. */
public class MechRocketRenderer extends GeoEntityRenderer<MechRocketEntity> {
    public MechRocketRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ProjectRebirth.id("mech_rocket")));
        this.shadowRadius = 0.0f;
    }

    @Override
    protected void applyRotations(MechRocketEntity rocket, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        poseStack.translate(0.0, 0.15, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - Mth.rotLerp(partialTick, rocket.yRotO, rocket.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, rocket.xRotO, rocket.getXRot())));
        poseStack.translate(0.0, -0.15, 0.0);
    }
}
