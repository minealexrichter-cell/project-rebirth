package com.projectrebirth.client.render;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.hostile.WalkingGunner;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The walking machines of part B3b: the patrol robot (its bone "head" turns and tilts to where it looks) and the mech (its bone
 * "torso" turns on the legs to where it looks). The machines do not fall over sideways when destroyed.
 */
public class WalkingGunnerRenderer<T extends WalkingGunner> extends GeoEntityRenderer<T> {
    private WalkingGunnerRenderer(EntityRendererProvider.Context context, String name, String turning, boolean tilts, float shadow, boolean glows) {
        super(context, new Model<>(name, turning, tilts));
        this.shadowRadius = shadow;
        if (glows) {
            addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
        }
    }

    public static <T extends WalkingGunner> WalkingGunnerRenderer<T> robot(EntityRendererProvider.Context context) {
        return new WalkingGunnerRenderer<>(context, "patrol_robot", "head", true, 0.5f, false);
    }

    public static <T extends WalkingGunner> WalkingGunnerRenderer<T> mech(EntityRendererProvider.Context context) {
        return new WalkingGunnerRenderer<>(context, "mech", "torso", false, 1.4f, true);
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        return 0.0f;
    }

    private static class Model<T extends WalkingGunner> extends GeoModel<T> {
        private final ResourceLocation model, texture, animation;
        private final String turning;
        private final boolean tilts;

        Model(String name, String turning, boolean tilts) {
            this.model = ProjectRebirth.id("geo/entity/" + name + ".geo.json");
            this.texture = ProjectRebirth.id("textures/entity/" + name + ".png");
            this.animation = ProjectRebirth.id("animations/entity/" + name + ".animation.json");
            this.turning = turning;
            this.tilts = tilts;
        }

        @Override
        public ResourceLocation getModelResource(T entity) {
            return model;
        }

        @Override
        public ResourceLocation getTextureResource(T entity) {
            return texture;
        }

        @Override
        public ResourceLocation getAnimationResource(T entity) {
            return animation;
        }

        @Override
        public void setCustomAnimations(T entity, long instanceId, AnimationState<T> state) {
            if (entity instanceof com.projectrebirth.hostile.MechEntity mech) {
                // the second phase: the chest plate and the rocket pod are gone (they fly off in the transform animation)
                boolean stripped = mech.phase() == 2;
                for (String part : new String[] {"plate_front", "left_pod"}) {
                    GeoBone piece = getAnimationProcessor().getBone(part);
                    if (piece != null) {
                        piece.setHidden(stripped);
                    }
                }
            }
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            GeoBone bone = getAnimationProcessor().getBone(turning);
            if (data == null || bone == null) {
                return;
            }
            bone.setRotY(Mth.clamp(data.netHeadYaw(), -75.0f, 75.0f) * Mth.DEG_TO_RAD);
            if (tilts) {
                bone.setRotX(data.headPitch() * Mth.DEG_TO_RAD);
            }
        }
    }
}
