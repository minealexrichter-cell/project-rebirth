package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.hostile.MutatedAnimalEntity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** 1.9.0, block C: a mutated cow, pig or chicken; a little larger than the animal it was. */
public class MutatedAnimalRenderer extends MobRenderer<MutatedAnimalEntity, MutatedAnimalModel> {
    private final ResourceLocation texture;
    private final float scale;

    public MutatedAnimalRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, String kind, boolean bird, float shadow, float scale) {
        super(context, new MutatedAnimalModel(context.bakeLayer(layer), bird), shadow);
        this.texture = ProjectRebirth.id("textures/entity/mutated_" + kind + ".png");
        this.scale = scale;
    }

    @Override
    protected void scale(MutatedAnimalEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(MutatedAnimalEntity entity) {
        return texture;
    }
}
