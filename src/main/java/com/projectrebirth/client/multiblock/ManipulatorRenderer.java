package com.projectrebirth.client.multiblock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.multiblock.ManipulatorBlockEntity;
import com.projectrebirth.multiblock.ManipulatorPose;
import com.projectrebirth.multiblock.MultiblockControllerBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * The manipulators' arms (1.7.0, block B), GeckoLib models by the kind of tool, bent every frame by {@link ManipulatorPose};
 * the gripper holds the part of the current stage while it carries it. Client only.
 */
public class ManipulatorRenderer extends GeoBlockRenderer<ManipulatorBlockEntity> {
    public ManipulatorRenderer(BlockEntityRendererProvider.Context context) {
        super(new Model());
    }

    @Override
    public void render(ManipulatorBlockEntity manipulator, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(manipulator, partialTick, pose, buffers, light, overlay);
        ManipulatorPose.Pose drawn = manipulator.lastPose();
        if (drawn == null || !drawn.carrying() || manipulator.getLevel() == null || manipulator.controller() == null
                || !(manipulator.getLevel().getBlockEntity(manipulator.controller()) instanceof MultiblockControllerBlockEntity controller)) {
            return;
        }
        ItemStack part = controller.previewPart();
        if (part.isEmpty()) {
            return;
        }
        Vec3 at = drawn.tip().subtract(Vec3.atLowerCornerOf(manipulator.getBlockPos()));
        pose.pushPose();
        pose.translate(at.x, at.y - 0.12, at.z);
        pose.scale(0.6f, 0.6f, 0.6f);
        Minecraft.getInstance().getItemRenderer().renderStatic(part, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                manipulator.getLevel(), 0);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ManipulatorBlockEntity manipulator) {
        // the telescopic arm reaches up to three blocks away
        return new AABB(manipulator.getBlockPos()).inflate(3.0);
    }

    /** The model of the arm's tool, with the arm set to the pose of this frame. */
    private static final class Model extends GeoModel<ManipulatorBlockEntity> {
        @Override
        public ResourceLocation getModelResource(ManipulatorBlockEntity manipulator) {
            return ProjectRebirth.id("geo/block/" + manipulator.tool().blockId() + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(ManipulatorBlockEntity manipulator) {
            return ProjectRebirth.id("textures/block/" + manipulator.tool().blockId() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(ManipulatorBlockEntity manipulator) {
            return ProjectRebirth.id("animations/block/manipulator.animation.json");
        }

        @Override
        public void setCustomAnimations(ManipulatorBlockEntity manipulator, long instanceId, AnimationState<ManipulatorBlockEntity> state) {
            ManipulatorPose.Pose pose = ManipulatorPose.compute(manipulator, state.getPartialTick());
            manipulator.setLastPose(pose);
            rotate("turret", 0.0f, pose.yaw(), 0.0f);
            rotate("lower_arm", pose.shoulder(), 0.0f, 0.0f);
            rotate("forearm", pose.elbow(), 0.0f, 0.0f);
            rotate("wrist", pose.wrist(), 0.0f, 0.0f);
            for (int i = 1; i <= 3; i++) {
                GeoBone stage = getAnimationProcessor().getBone("extension_" + i);
                if (stage != null) {
                    stage.setPosZ(-pose.extension());
                }
            }
            float open = pose.fingers();
            rotate("finger_top", open, 0.0f, 0.0f);
            rotate("finger_left", -open * 0.5f, open * 0.87f, 0.0f);
            rotate("finger_right", -open * 0.5f, -open * 0.87f, 0.0f);
            rotate("bit", 0.0f, 0.0f, pose.spin());
        }

        private void rotate(String name, float x, float y, float z) {
            GeoBone bone = getAnimationProcessor().getBone(name);
            if (bone != null) {
                bone.setRotX(x);
                bone.setRotY(y);
                bone.setRotZ(z);
            }
        }
    }
}
