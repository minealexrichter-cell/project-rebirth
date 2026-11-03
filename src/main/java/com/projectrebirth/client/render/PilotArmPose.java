package com.projectrebirth.client.render;

import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Copies the vanilla player arm poses (drawing a bow, aiming or charging a crossbow, raising a trident, blocking
 * with a shield, ...) onto the armor's arms. The poses are computed by a hidden vanilla player model, so they match
 * exactly what a normal player looks like; the armor's own animations keep the arms in every other case.
 */
final class PilotArmPose {
    /** An arm holding an item is carried slightly forward, like a player's (vanilla uses the same 18 degrees). */
    private static final float HOLD_RAISE = (float) Math.toRadians(18.0);
    /** Highest arm angle of the armor's punch; the lift fades out towards it so the punch keeps its shape. */
    private static final float PUNCH_PEAK = (float) Math.toRadians(125.0);
    private static HumanoidModel<LivingEntity> model;

    private PilotArmPose() {
    }

    static void apply(PowerArmorEntity armor, Player pilot, float partialTick, GeoBone rightArm, GeoBone leftArm) {
        HumanoidModel.ArmPose rightPose = sidePose(pilot, HumanoidArm.RIGHT);
        HumanoidModel.ArmPose leftPose = sidePose(pilot, HumanoidArm.LEFT);
        boolean poseRight = isPosed(rightPose, leftPose);
        boolean poseLeft = isPosed(leftPose, rightPose);
        boolean punching = isPunching(armor);
        lift(rightArm, holdsPlainItem(pilot, HumanoidArm.RIGHT), punching);
        lift(leftArm, holdsPlainItem(pilot, HumanoidArm.LEFT), punching);
        if (!poseRight && !poseLeft) {
            return;
        }

        if (model == null) {
            model = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER));
        }
        model.rightArmPose = rightPose;
        model.leftArmPose = leftPose;
        model.attackTime = 0.0f;
        model.riding = false;
        model.crouching = false;
        model.young = false;
        float headYaw = Mth.wrapDegrees(pilot.getViewYRot(partialTick) - Mth.rotLerp(partialTick, armor.yBodyRotO, armor.yBodyRot));
        // Age 0: no idle arm sway, the armor has its own.
        model.setupAnim(pilot, 0.0f, 0.0f, 0.0f, headYaw, pilot.getViewXRot(partialTick));

        if (poseRight) {
            copy(model.rightArm, rightArm);
        }
        if (poseLeft) {
            copy(model.leftArm, leftArm);
        }
    }

    /**
     * The armor's animations set the arm angles anew every frame, so the lift is added on top of them. During the
     * punch the lift fades as the arm goes up, so there is no jump when the punch starts or ends.
     */
    private static void lift(GeoBone arm, boolean holding, boolean punching) {
        if (holding) {
            float animated = arm.getRotX();
            arm.setRotX(animated + (punching ? HOLD_RAISE * Mth.clamp(1.0f - animated / PUNCH_PEAK, 0.0f, 1.0f) : HOLD_RAISE));
        }
    }

    /** Only a plain held item lifts the arm, not the vanilla item poses. */
    private static boolean holdsPlainItem(Player pilot, HumanoidArm side) {
        HumanoidModel.ArmPose pose = sidePose(pilot, side);
        return pose == HumanoidModel.ArmPose.ITEM && !isPosed(pose, sidePose(pilot, side.getOpposite()));
    }

    private static boolean isPunching(PowerArmorEntity armor) {
        AnimationController<GeoAnimatable> action = armor.getAnimatableInstanceCache().getManagerForId(armor.getId())
                .getAnimationControllers().get(PowerArmorEntity.ACTION_CONTROLLER);
        return action != null && action.getAnimationState() == AnimationController.State.RUNNING;
    }

    /** Pose of the arm on the given side, with the vanilla rule that a two-handed item frees the other hand. */
    private static HumanoidModel.ArmPose sidePose(Player pilot, HumanoidArm side) {
        HumanoidModel.ArmPose mainPose = armPose(pilot, InteractionHand.MAIN_HAND);
        if (side == pilot.getMainArm()) {
            return mainPose;
        }
        if (mainPose.isTwoHanded()) {
            return pilot.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }
        return armPose(pilot, InteractionHand.OFF_HAND);
    }

    private static boolean isPosed(HumanoidModel.ArmPose pose, HumanoidModel.ArmPose other) {
        return isSpecial(pose) || other.isTwoHanded();
    }

    /** Same choice as the vanilla player renderer. */
    private static HumanoidModel.ArmPose armPose(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (player.getUsedItemHand() == hand && player.getUseItemRemainingTicks() > 0) {
            switch (stack.getUseAnimation()) {
                case BLOCK -> { return HumanoidModel.ArmPose.BLOCK; }
                case BOW -> { return HumanoidModel.ArmPose.BOW_AND_ARROW; }
                case SPEAR -> { return HumanoidModel.ArmPose.THROW_SPEAR; }
                case CROSSBOW -> { return HumanoidModel.ArmPose.CROSSBOW_CHARGE; }
                case SPYGLASS -> { return HumanoidModel.ArmPose.SPYGLASS; }
                case TOOT_HORN -> { return HumanoidModel.ArmPose.TOOT_HORN; }
                case BRUSH -> { return HumanoidModel.ArmPose.BRUSH; }
                default -> { }
            }
        } else if (!player.swinging && stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        HumanoidModel.ArmPose modded = IClientItemExtensions.of(stack).getArmPose(player, hand, stack);
        return modded != null ? modded : HumanoidModel.ArmPose.ITEM;
    }

    private static boolean isSpecial(HumanoidModel.ArmPose pose) {
        return pose != HumanoidModel.ArmPose.EMPTY && pose != HumanoidModel.ArmPose.ITEM;
    }

    /** Vanilla model space is upside down compared to GeckoLib's (a half turn around Z): X and Y angles flip sign. */
    private static void copy(ModelPart part, GeoBone bone) {
        bone.setRotX(-part.xRot);
        bone.setRotY(-part.yRot);
        bone.setRotZ(part.zRot);
    }
}
