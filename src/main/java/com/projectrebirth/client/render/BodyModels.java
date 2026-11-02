package com.projectrebirth.client.render;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.BodyClass;

import net.minecraft.resources.ResourceLocation;
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

    /**
     * @param turnsHead true to point the "head" bone where the entity looks (overrides the head part of animations)
     */
    public static <T extends GeoAnimatable> DefaultedEntityGeoModel<T> create(BodyClass bodyClass, boolean turnsHead) {
        return new DefaultedEntityGeoModel<T>(ProjectRebirth.id(bodyClass.id()), turnsHead)
                .withAltAnimations(ProjectRebirth.id("body"));
    }

    public static ResourceLocation texture(BodyClass bodyClass) {
        return ProjectRebirth.id("textures/entity/" + bodyClass.id() + ".png");
    }
}
