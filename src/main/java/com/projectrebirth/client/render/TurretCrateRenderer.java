package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projectrebirth.garrison.GarrisonRegistry;
import com.projectrebirth.garrison.TurretCrateEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** 1.7.1: the crate of a turret rolled out of the heavy dock: the crate block, the turret's icon on its lid. Client only. */
public class TurretCrateRenderer extends EntityRenderer<TurretCrateEntity> {
    private final EntityRendererProvider.Context context;

    public TurretCrateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.context = context;
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(TurretCrateEntity crate, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(-0.49, 0.0, -0.49);
        pose.scale(0.98f, 0.98f, 0.98f);
        context.getBlockRenderDispatcher().renderSingleBlock(GarrisonRegistry.TURRET_CRATE_BLOCK.get().defaultBlockState(), pose, buffers, light,
                OverlayTexture.NO_OVERLAY);
        ItemStack stack = crate.getItem();
        if (!stack.isEmpty()) {
            pose.translate(0.5, 1.005, 0.5);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
            pose.scale(0.7f, 0.7f, 0.7f);
            context.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, crate.level(),
                    crate.getId());
        }
        pose.popPose();
        super.render(crate, yaw, partialTick, pose, buffers, light);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(TurretCrateEntity crate) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
