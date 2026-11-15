package com.projectrebirth.client.render;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.hostile.MutantEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The mutants: their head (bone "head") turns to where they look. The glowing ghoul's sores glow in the dark: the texture
 * "glowing_ghoul_glowmask.png" is drawn full bright over the body.
 */
public class MutantRenderer<T extends MutantEntity> extends GeoEntityRenderer<T> {
    public MutantRenderer(EntityRendererProvider.Context context, String name, float shadow, boolean glows) {
        super(context, new DefaultedEntityGeoModel<T>(ProjectRebirth.id(name)) {
            @Override
            public void setCustomAnimations(T mutant, long instanceId, AnimationState<T> state) {
                EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
                GeoBone head = getAnimationProcessor().getBone("head");
                if (data != null && head != null) {
                    head.setRotY(Mth.clamp(data.netHeadYaw(), -60.0f, 60.0f) * Mth.DEG_TO_RAD);
                    head.setRotX(data.headPitch() * Mth.DEG_TO_RAD);
                }
            }
        });
        this.shadowRadius = shadow;
        if (glows) {
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }
}
