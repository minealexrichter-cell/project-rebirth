package com.projectrebirth.client.render;

import com.projectrebirth.hostile.HunterEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.util.Color;

/**
 * 1.9.0, block D: the hunter. Hidden (night or a storm, no light) it is drawn nearly clear: only a shimmer shows where it is,
 * and it fades in or out over half a second.
 */
public class HunterRenderer extends MutantRenderer<HunterEntity> {
    /** How much of it shows while hidden. */
    private static final float HIDDEN = 0.08f;

    public HunterRenderer(EntityRendererProvider.Context context) {
        super(context, "hunter", 0.5f, false);
    }

    private static float alpha(HunterEntity hunter) {
        return hunter.isCloaked() ? HIDDEN : 1.0f;
    }

    @Override
    public Color getRenderColor(HunterEntity hunter, float partialTick, int packedLight) {
        return Color.ofRGBA(1.0f, 1.0f, 1.0f, alpha(hunter));
    }

    @Override
    public RenderType getRenderType(HunterEntity hunter, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return hunter.isCloaked() ? RenderType.entityTranslucent(texture) : super.getRenderType(hunter, texture, bufferSource, partialTick);
    }
}
