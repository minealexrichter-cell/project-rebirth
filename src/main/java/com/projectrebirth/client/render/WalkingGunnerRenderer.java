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

    /** The muzzle whose flash shows: the robot's gun; the mech's machine gun, or its gatling in the second phase. */
    private final java.util.List<com.mojang.blaze3d.vertex.PoseStack.Pose> flashes = new java.util.ArrayList<>();

    @Override
    public void render(T entity, float entityYaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        flashes.clear();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
        float age = MuzzleFlash.age(entity.getId(), partialTick);
        float scale = entity instanceof com.projectrebirth.hostile.MechEntity ? 1.0f : 0.7f;
        for (com.mojang.blaze3d.vertex.PoseStack.Pose pose : flashes) {
            MuzzleFlash.draw(pose, buffers, age, MuzzleFlash.seed(entity.getId()), scale);
        }
        flashes.clear();
    }

    @Override
    public void renderRecursively(com.mojang.blaze3d.vertex.PoseStack poseStack, T entity, GeoBone bone, net.minecraft.client.renderer.RenderType renderType,
                                  net.minecraft.client.renderer.MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender && MuzzleFlash.age(entity.getId(), partialTick) < 1.0f) {
            String name = bone.getName();
            boolean second = entity instanceof com.projectrebirth.hostile.MechEntity mech && mech.phase() == 2;
            if (name.equals("muzzle") || name.equals(second ? "gatling_muzzle" : "gun_muzzle")) {
                flashes.add(MuzzleFlash.capture(poseStack, bone));
            }
        }
        super.renderRecursively(poseStack, entity, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
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

        /**
         * Aiming (part B3j): with a target the gun comes up and follows it up and down and to the sides (it turns at its own joint,
         * which the animations never touch); without one it hangs lowered. The robot turns its gun at the elbow; the mech its
         * machine gun at the shoulder and its gatling on its mount, while its torso already turns toward the target.
         */
        private void aim(T entity, EntityModelData data, double partialTick) {
            if (data == null) {
                return;
            }
            float aim = entity.aimAmount((float) partialTick);
            float pitch = Mth.clamp(data.headPitch(), -60.0f, 60.0f) * Mth.DEG_TO_RAD;
            boolean mech = entity instanceof com.projectrebirth.hostile.MechEntity;
            // the robot's head turns fully, its body follows later: the gun takes up the rest of the turn
            float yaw = mech ? 0.0f : Mth.clamp(data.netHeadYaw(), -45.0f, 45.0f) * Mth.DEG_TO_RAD;
            float lowered = mech ? 0.3f : 0.55f;
            for (String name : new String[] {"gun_mount", "gatling_aim"}) {
                GeoBone joint = getAnimationProcessor().getBone(name);
                if (joint == null) {
                    continue;
                }
                float down = name.equals("gatling_aim") ? 0.0f : lowered * (1.0f - aim);
                joint.setRotX(joint.getInitialSnapshot().getRotX() + down + pitch * aim);
                joint.setRotY(joint.getInitialSnapshot().getRotY() + yaw * aim);
            }
        }

        @Override
        public void setCustomAnimations(T entity, long instanceId, AnimationState<T> state) {
            if (entity instanceof com.projectrebirth.hostile.MechEntity mech) {
                // the second phase: the chest plate and the rocket pod are gone (they fly off in the transform animation)
                boolean stripped = mech.phase() == 2;
                // the gatling exists only from the transform on
                GeoBone gatling = getAnimationProcessor().getBone("gatling_mount");
                if (gatling != null) {
                    gatling.setHidden(mech.phase() == 0);
                }
                for (String part : new String[] {"plate_front", "left_pod"}) {
                    GeoBone piece = getAnimationProcessor().getBone(part);
                    if (piece != null) {
                        piece.setHidden(stripped);
                    }
                }
            }
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            aim(entity, data, state.getPartialTick());
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
