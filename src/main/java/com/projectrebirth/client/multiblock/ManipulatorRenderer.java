package com.projectrebirth.client.multiblock;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.multiblock.ManipulatorBlockEntity;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** The manipulator's arm, a GeckoLib model (1.7.0, block B). Client only. */
public class ManipulatorRenderer extends GeoBlockRenderer<ManipulatorBlockEntity> {
    public ManipulatorRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<>(ProjectRebirth.id("manipulator")));
    }
}
