package com.projectrebirth.client.render;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Renders an unoccupied body of any class. */
public class ArtificialBodyRenderer extends GeoEntityRenderer<ArtificialBodyEntity> {
    public ArtificialBodyRenderer(EntityRendererProvider.Context context, BodyClass bodyClass) {
        super(context, BodyModels.create(bodyClass));
        this.shadowRadius = 0.4f;
    }
}
