package com.projectrebirth.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * A gun outside power armor is a heavy load: both arms down in front of the body, the body leaning forward, a slow sway
 * while walking. The pose has no effect on the game. Artificial bodies get the arms from the vanilla model (see
 * PilotArmPose), so the two look the same.
 */
public final class HeavyWeaponPose {
    private static final float ARM_FORWARD = 0.36f;
    private static final float ARM_INWARD = 0.32f;
    private static final float ARM_SWAY = 0.1f;
    private static final float SWAY_RATE = 0.4f;
    private static final float WHOLE_BODY_SWAY = 3.0f;
    /** Lean of the body, in radians. */
    public static final float LEAN = 0.2f;
    private static final float LEAN_SWAY = 0.04f;

    /** The value of the enum is added by META-INF/enumextensions.json. */
    public static final EnumProxy<HumanoidModel.ArmPose> POSE = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) HeavyWeaponPose::poseArms);

    private HeavyWeaponPose() {
    }

    private static float sway(LivingEntity entity) {
        return Mth.sin(entity.walkAnimation.position() * SWAY_RATE) * Math.min(1.0f, entity.walkAnimation.speed());
    }

    /** Both arms at once: the pose of the other arm is "empty", which leaves its forward and sideways angles alone. */
    private static void poseArms(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float sway = sway(entity) * ARM_SWAY;
        model.rightArm.xRot = -ARM_FORWARD + sway;
        model.rightArm.yRot = 0.0f;
        model.rightArm.zRot = -ARM_INWARD;
        model.leftArm.xRot = -ARM_FORWARD - sway;
        model.leftArm.yRot = 0.0f;
        model.leftArm.zRot = ARM_INWARD;
    }

    /** How far a body of bones leans forward now, swaying with its steps; positive leans forward. */
    public static float boneLean(LivingEntity entity) {
        return LEAN + sway(entity) * LEAN_SWAY;
    }

    /**
     * The player model's body cannot lean on its own (vanilla sets its angle after the arm poses), so the whole figure
     * leans a little from the feet.
     */
    public static void leanWholeFigure(PoseStack poseStack, LivingEntity entity, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotation(LEAN * 0.6f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sway(entity) * WHOLE_BODY_SWAY));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
    }
}
