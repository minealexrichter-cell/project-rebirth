package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Power armor; a wreck uses a burnt texture. The pilot does not see their own armor in first person. */
public class PowerArmorRenderer extends GeoEntityRenderer<PowerArmorEntity> {
    private static final ResourceLocation WRECK_TEXTURE = ProjectRebirth.id("textures/entity/power_armor_wrecked.png");

    public PowerArmorRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ProjectRebirth.id("power_armor"), false));
        this.shadowRadius = 0.8f;
    }

    @Override
    public ResourceLocation getTextureLocation(PowerArmorEntity armor) {
        return armor.isWrecked() ? WRECK_TEXTURE : super.getTextureLocation(armor);
    }

    @Override
    public void render(PowerArmorEntity armor, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && armor.hasPassenger(minecraft.player) && minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        super.render(armor, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
