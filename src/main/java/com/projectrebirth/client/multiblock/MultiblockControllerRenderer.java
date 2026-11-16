package com.projectrebirth.client.multiblock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projectrebirth.multiblock.MultiblockControllerBlockEntity;
import com.projectrebirth.multiblock.MultiblockSchema;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws the machine under assembly in the zone of a working multiblock (1.7.0, block B). Client only. */
public class MultiblockControllerRenderer implements BlockEntityRenderer<MultiblockControllerBlockEntity> {
    public MultiblockControllerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MultiblockControllerBlockEntity controller, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (controller.previewStage() < 0 || controller.previewResult().isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }
        MultiblockSchema schema = controller.schema();
        if (schema == null) {
            return;
        }
        Entity entity = AssemblyPreview.entityFor(controller.previewResult(), Minecraft.getInstance().level);
        if (entity == null) {
            return;
        }
        BlockPos origin = controller.getBlockPos();
        Vec3 at = schema.displayPoint(origin, controller.facing());
        pose.pushPose();
        pose.translate(at.x - origin.getX(), at.y - origin.getY(), at.z - origin.getZ());
        int glow = LevelRenderer.getLightColor(Minecraft.getInstance().level, BlockPos.containing(at).above());
        AssemblyPreview.render(entity, controller.previewStage(), controller.facing().toYRot(), pose, buffers, glow, partialTick);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(MultiblockControllerBlockEntity controller) {
        return controller.renderBounds();
    }

    @Override
    public boolean shouldRenderOffScreen(MultiblockControllerBlockEntity controller) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
