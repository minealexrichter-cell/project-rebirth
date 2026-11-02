package com.projectrebirth.client.render;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Geo models of body classes. Model and texture are per class
 * (geo/entity/&lt;id&gt;.geo.json, textures/entity/&lt;id&gt;.png); all classes share one animation file,
 * animations/entity/body.animation.json, so every model must use the same bone names.
 */
public final class BodyModels {
    private BodyModels() {
    }

    public static <T extends GeoAnimatable> DefaultedEntityGeoModel<T> create(BodyClass bodyClass) {
        return new DefaultedEntityGeoModel<T>(ProjectRebirth.id(bodyClass.id()), false)
                .withAltAnimations(ProjectRebirth.id("body"));
    }
}
