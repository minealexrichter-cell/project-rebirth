package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Power armor; a wreck uses a burnt texture. The pilot does not see their own armor in first person.
 * The pilot's held items are drawn in the armor's fists (bones "right_hand" and "left_hand"), and the arms take
 * the pilot's item poses (bow, crossbow, trident, shield).
 */
public class PowerArmorRenderer extends GeoEntityRenderer<PowerArmorEntity> {
    private static final ResourceLocation WRECK_TEXTURE = ProjectRebirth.id("textures/entity/power_armor_wrecked.png");
    private static final String RIGHT_HAND = "right_hand";
    private static final String LEFT_HAND = "left_hand";
    /** A bit larger than in a player's hand to suit the big fists, but small enough to stay off the ground. */
    private static final float ITEM_SCALE = 1.25f;

    public PowerArmorRenderer(EntityRendererProvider.Context context) {
        super(context, new ArmorModel());
        this.shadowRadius = 0.8f;
        addRenderLayer(new HeldItemLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(PowerArmorEntity armor) {
        return armor.isWrecked() ? WRECK_TEXTURE : super.getTextureLocation(armor);
    }

    @Override
    public void render(PowerArmorEntity armor, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && armor.hasPassenger(minecraft.player) && minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        super.render(armor, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private static class ArmorModel extends DefaultedEntityGeoModel<PowerArmorEntity> {
        ArmorModel() {
            super(ProjectRebirth.id("power_armor"), false);
        }

        @Override
        public void setCustomAnimations(PowerArmorEntity armor, long instanceId, AnimationState<PowerArmorEntity> state) {
            super.setCustomAnimations(armor, instanceId, state);
            PilotArmPose.reset();
            Player pilot = armor.getPilot();
            if (pilot == null || !armor.isActive()) {
                return;
            }
            GeoBone rightArm = getAnimationProcessor().getBone("right_arm");
            GeoBone leftArm = getAnimationProcessor().getBone("left_arm");
            if (rightArm != null && leftArm != null) {
                PilotArmPose.apply(armor, pilot, state.getPartialTick(), rightArm, leftArm);
            }
        }
    }

    private static class HeldItemLayer extends BlockAndItemGeoLayer<PowerArmorEntity> {
        HeldItemLayer(PowerArmorRenderer renderer) {
            super(renderer);
        }

        /** Main hand goes to the fist on the pilot's main-arm side, the off hand to the other fist. */
        @Override
        protected ItemStack getStackForBone(GeoBone bone, PowerArmorEntity armor) {
            Player pilot = armor.getPilot();
            if (pilot == null || armor.isWrecked()) {
                return null;
            }
            boolean rightFist = bone.getName().equals(RIGHT_HAND);
            if (!rightFist && !bone.getName().equals(LEFT_HAND)) {
                return null;
            }
            boolean mainHandIsRight = pilot.getMainArm() == HumanoidArm.RIGHT;
            ItemStack stack = rightFist == mainHandIsRight ? pilot.getMainHandItem() : pilot.getOffhandItem();
            return stack.isEmpty() ? null : stack;
        }

        /**
         * Same transform as the vanilla held-item layer, so every item (sword handle, shield, bow, trident, crossbow)
         * sits in the fist exactly as in a player's hand. The bone is at the bottom of the fist, where the vanilla
         * layer ends up after moving down the arm.
         */
        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, PowerArmorEntity armor,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            Player pilot = armor.getPilot();
            if (pilot == null) {
                return;
            }
            boolean left = bone.getName().equals(LEFT_HAND);
            // Undo the lift of an arm holding an item: the item rises with the fist but is not tilted.
            poseStack.mulPose(Axis.XP.rotation(-PilotArmPose.liftOf(left ? HumanoidArm.LEFT : HumanoidArm.RIGHT)));
            // GeckoLib space -> vanilla model space (which is upside down).
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            poseStack.translate((left ? -1.0f : 1.0f) / 16.0f, 0.125f, 0.0f);
            // Rendering for the pilot gives the item its live look: bow pulling, loaded crossbow and so on.
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(pilot, stack,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, poseStack, bufferSource, packedLight);
        }
    }
}
