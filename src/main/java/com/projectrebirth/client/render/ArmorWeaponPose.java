package com.projectrebirth.client.render;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.powerarmor.PowerArmorEntity;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.weapons.Weapons;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * The arms of a power armor holding a weapon, as others see it: a hold at the hip, higher when aiming, the weapon hand
 * jolted by shots, the other hand going down to the magazine during a reload. The weapon follows the pilot's look.
 */
final class ArmorWeaponPose {
    private static final float HOLD_ARM = 0.45f;
    private static final float HOLD_FOREARM = 0.95f;
    private static final float AIM_ARM = 0.55f;
    private static final float AIM_FOREARM = -0.15f;
    private static final float KICK = 0.12f;
    private static final float MAIN_INWARD = 0.3f;
    private static final float SUPPORT_AIM = 0.25f;
    private static final float SUPPORT_ARM = 0.6f;
    private static final float SUPPORT_FOREARM = 0.95f;
    private static final float SUPPORT_INWARD = 0.6f;
    private static final float RELOAD_DIP = 0.7f;
    /** Share of the look pitch that the upper arms follow; the rest is the forearms' and the weapon's. */
    private static final float LOOK_FOLLOW = 0.75f;

    /** The angle the weapon must turn back by to stay along the look; read by the renderer right after this pose. */
    private static float weaponCorrection;

    private ArmorWeaponPose() {
    }

    static float weaponCorrection() {
        return weaponCorrection;
    }

    static void apply(PowerArmorEntity armor, Player pilot, float partialTick, GeoBone rightArm, GeoBone leftArm,
                      @Nullable GeoBone rightForearm, @Nullable GeoBone leftForearm) {
        WeaponView view = WeaponView.of(pilot, pilot.getMainHandItem().getItem());
        view.update(pilot, partialTick);
        pose(pilot.getMainArm() == HumanoidArm.RIGHT, pilot.getViewXRot(partialTick) * Mth.DEG_TO_RAD, view.aimAmount(), view.kick(),
                RELOAD_DIP * reloadDip(armor, pilot), rightArm, leftArm, rightForearm, leftForearm);
    }

    /**
     * 1.9.0, block E: the same hold for a wanderer in power armor (no pilot): the weapon in the right fist, the look's
     * pitch from the wanderer itself, raised to aim while it fights.
     */
    static void applyWanderer(net.minecraft.world.entity.LivingEntity wanderer, float aim, float partialTick, GeoBone rightArm, GeoBone leftArm,
                              @Nullable GeoBone rightForearm, @Nullable GeoBone leftForearm) {
        pose(true, wanderer.getViewXRot(partialTick) * Mth.DEG_TO_RAD, aim, 0.0f, 0.0f, rightArm, leftArm, rightForearm, leftForearm);
    }

    private static void pose(boolean right, float pitch, float aim, float kick, float dip, GeoBone rightArm, GeoBone leftArm,
                             @Nullable GeoBone rightForearm, @Nullable GeoBone leftForearm) {
        GeoBone mainArm = right ? rightArm : leftArm;
        GeoBone mainForearm = right ? rightForearm : leftForearm;
        GeoBone otherArm = right ? leftArm : rightArm;
        GeoBone otherForearm = right ? leftForearm : rightForearm;
        float side = right ? 1.0f : -1.0f;

        float arm = HOLD_ARM + AIM_ARM * aim - pitch * LOOK_FOLLOW + kick * KICK;
        float forearm = HOLD_FOREARM + AIM_FOREARM * aim;
        mainArm.setRotX(arm);
        mainArm.setRotY(side * MAIN_INWARD);
        mainArm.setRotZ(0.0f);
        if (mainForearm != null) {
            mainForearm.setRotX(forearm);
        }
        weaponCorrection = arm + forearm + pitch;

        otherArm.setRotX(SUPPORT_ARM + SUPPORT_AIM * aim - pitch * LOOK_FOLLOW - dip);
        otherArm.setRotY(-side * SUPPORT_INWARD);
        otherArm.setRotZ(0.0f);
        if (otherForearm != null) {
            otherForearm.setRotX(SUPPORT_FOREARM + AIM_FOREARM * aim);
        }
    }

    /** 0 to 1 and back within each reload step: the hand goes to the magazine and returns. */
    private static float reloadDip(PowerArmorEntity armor, Player pilot) {
        long end = armor.weaponReloadEnd();
        RebirthConfig.WeaponStats stats = Weapons.statsOf(pilot.getMainHandItem().getItem());
        if (end <= 0L || stats == null) {
            return 0.0f;
        }
        float left = (end - armor.level().getGameTime()) / (float) stats.reloadTicks().get();
        return Mth.sin(Mth.clamp(1.0f - left, 0.0f, 1.0f) * Mth.PI);
    }
}
