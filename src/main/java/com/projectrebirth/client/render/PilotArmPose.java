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
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Copies the vanilla player arm poses (drawing a bow, aiming or charging a crossbow, raising a trident, blocking
 * with a shield, ...) onto the armor's arms. The poses are computed by a hidden vanilla player model, so they match
 * exactly what a normal player looks like; the armor's own animations keep the arms in every other case.
 */
final class PilotArmPose {
    private static HumanoidModel<LivingEntity> model;

    private PilotArmPose() {
    }

    static void apply(PowerArmorEntity armor, Player pilot, float partialTick, GeoBone rightArm, GeoBone leftArm) {
        HumanoidArm mainArm = pilot.getMainArm();
        HumanoidModel.ArmPose mainPose = armPose(pilot, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offPose = armPose(pilot, InteractionHand.OFF_HAND);
        if (mainPose.isTwoHanded()) {
            offPose = pilot.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }
        HumanoidModel.ArmPose rightPose = mainArm == HumanoidArm.RIGHT ? mainPose : offPose;
        HumanoidModel.ArmPose leftPose = mainArm == HumanoidArm.RIGHT ? offPose : mainPose;
        boolean poseRight = isSpecial(rightPose) || leftPose.isTwoHanded();
        boolean poseLeft = isSpecial(leftPose) || rightPose.isTwoHanded();
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
