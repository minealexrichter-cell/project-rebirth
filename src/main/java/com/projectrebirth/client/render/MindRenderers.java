package com.projectrebirth.client.render;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.machinemind.MindDroneEntity;
import com.projectrebirth.machinemind.MindModuleBlockEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** 1.9.0, block F: the modules of a node of the machine mind (the computer by its stage) and its drone, all GeckoLib with glowing lines. */
public final class MindRenderers {
    private MindRenderers() {
    }

    public static class Module<T extends MindModuleBlockEntity> extends GeoBlockRenderer<T> {
        public Module(BlockEntityRendererProvider.Context context) {
            super(new ModuleModel<>());
            addRenderLayer(new AutoGlowingGeoLayer<>(this) {
                /** A dead node does not glow. */
                @Override
                public void render(PoseStack poseStack, T module, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                                   VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                    if (module.shownActive()) {
                        super.render(poseStack, module, model, renderType, buffers, buffer, partialTick, packedLight, packedOverlay);
                    }
                }
            });
        }

        @Override
        public AABB getRenderBoundingBox(T module) {
            return new AABB(module.getBlockPos()).inflate(3.0, 0.0, 3.0).expandTowards(0.0, 5.0, 0.0);
        }

        @Override
        public int getViewDistance() {
            return 128;
        }
    }

    private static class ModuleModel<T extends MindModuleBlockEntity> extends GeoModel<T> {
        private static String name(MindModuleBlockEntity module) {
            return module.module().model(module.tier());
        }

        @Override
        public ResourceLocation getModelResource(T module) {
            return ProjectRebirth.id("geo/block/" + name(module) + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(T module) {
            return ProjectRebirth.id("textures/block/" + name(module) + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(T module) {
            return ProjectRebirth.id("animations/block/" + name(module) + ".animation.json");
        }
    }

    public static class Drone extends GeoEntityRenderer<MindDroneEntity> {
        public Drone(EntityRendererProvider.Context context) {
            super(context, new DroneModel());
            this.shadowRadius = 0.35f;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    private static class DroneModel extends GeoModel<MindDroneEntity> {
        @Override
        public ResourceLocation getModelResource(MindDroneEntity drone) {
            return ProjectRebirth.id("geo/entity/mind_drone.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(MindDroneEntity drone) {
            return ProjectRebirth.id("textures/entity/mind_drone.png");
        }

        @Override
        public ResourceLocation getAnimationResource(MindDroneEntity drone) {
            return ProjectRebirth.id("animations/entity/mind_drone.animation.json");
        }
    }
}
