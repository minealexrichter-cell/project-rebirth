package com.projectrebirth.client.render;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders any body class. Assets are looked up by the class id:
 * geo/entity/&lt;id&gt;.geo.json, textures/entity/&lt;id&gt;.png, animations/entity/&lt;id&gt;.animation.json.
 */
public class ArtificialBodyRenderer extends GeoEntityRenderer<ArtificialBodyEntity> {
    public ArtificialBodyRenderer(EntityRendererProvider.Context context, BodyClass bodyClass) {
        super(context, new DefaultedEntityGeoModel<>(ProjectRebirth.id(bodyClass.id()), false));
        this.shadowRadius = 0.4f;
    }
}
