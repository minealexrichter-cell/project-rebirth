package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Renders an unoccupied body of any class, with the parts of its gadgets. */
public class ArtificialBodyRenderer extends GeoEntityRenderer<ArtificialBodyEntity> {
    private int platingColor = UpgradeVisuals.NO_TINT;

    public ArtificialBodyRenderer(EntityRendererProvider.Context context, BodyClass bodyClass) {
        super(context, BodyModels.create(bodyClass, false));
        this.shadowRadius = 0.4f;
    }

    @Override
    public void preRender(PoseStack poseStack, ArtificialBodyEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        platingColor = UpgradeVisuals.applyToBody(getGeoModel(), animatable.registryAccess(), animatable.upgrades());
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ArtificialBodyEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                bone.getName().startsWith(UpgradeVisuals.BODY_PLATING_PREFIX) ? platingColor : colour);
    }
}
