package com.projectrebirth.client.logistics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projectrebirth.client.multiblock.HallShell;
import com.projectrebirth.logistics.StorageControllerBlockEntity;
import com.projectrebirth.multiblock.MultiblockSchema;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** 1.8.0: draws the hall of a whole storage, as over the body workshop and the heavy dock (HallShell). Client only. */
public class StorageRenderer implements BlockEntityRenderer<StorageControllerBlockEntity> {
    public StorageRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StorageControllerBlockEntity storage, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MultiblockSchema schema = storage.schema();
        if (schema != null && storage.formed()) {
            HallShell.render(schema, storage.getBlockPos(), storage.facing(), pose, buffers);
        }
    }

    @Override
    public AABB getRenderBoundingBox(StorageControllerBlockEntity storage) {
        return storage.renderBounds();
    }

    @Override
    public boolean shouldRenderOffScreen(StorageControllerBlockEntity storage) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
