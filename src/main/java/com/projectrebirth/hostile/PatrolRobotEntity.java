package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The patrol robot of the ruins (1.6.0, part B3b): a two-metre walker with a gun for a right arm and a heavy left fist. It
 * walks round its post in the military base and the outpost, shoots a player it sees in short bursts and hits one who comes
 * up close. Drops parts. Numbers in the config ([hostile.patrol_robot]).
 */
public class PatrolRobotEntity extends WalkingGunner {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.patrol_robot.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.patrol_robot.walk");
    private static final RawAnimation SHOT = RawAnimation.begin().thenPlay("animation.patrol_robot.shot");
    private static final RawAnimation MELEE = RawAnimation.begin().thenPlay("animation.patrol_robot.melee");
    private static final double MELEE_REACH = 2.3;

    private int meleeCooldown;

    public PatrolRobotEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected boolean enabled() {
        return hostileOn() && (!RebirthConfig.SERVER_SPEC.isLoaded() || RebirthConfig.ROBOT_ENABLED.get());
    }

    @Override
    protected void applyConfig() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(RebirthConfig.ROBOT_HEALTH.get());
        getAttribute(Attributes.ARMOR).setBaseValue(RebirthConfig.ROBOT_ARMOR.get());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RebirthConfig.ROBOT_SPEED.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(RebirthConfig.ROBOT_MELEE.get());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(RebirthConfig.ROBOT_RANGE.get() + 8.0);
    }

    @Override
    protected double range() {
        return RebirthConfig.ROBOT_RANGE.get();
    }

    @Override
    protected int postRadius() {
        return RebirthConfig.ROBOT_RADIUS.get();
    }

    @Override
    protected double spread() {
        return RebirthConfig.ROBOT_SPREAD.get();
    }

    @Override
    protected float bulletDamage() {
        return RebirthConfig.ROBOT_DAMAGE.get().floatValue();
    }

    @Override
    protected int burst() {
        return RebirthConfig.ROBOT_BURST.get();
    }

    @Override
    protected int shotInterval() {
        return RebirthConfig.ROBOT_SHOT_INTERVAL.get();
    }

    @Override
    protected int burstPause() {
        return RebirthConfig.ROBOT_BURST_PAUSE.get();
    }

    @Override
    protected int lockTicks() {
        return 15;
    }

    /** The gun of its right arm, held forward at chest height. */
    @Override
    protected Vec3 muzzle() {
        return position().add(0.0, 1.06, 0.0).add(right().scale(0.69)).add(forward().scale(1.06));
    }

    @Override
    protected float shotPitch() {
        return 1.05f;
    }

    @Override
    protected void onShot() {
        triggerAnim("action", "shot");
    }

    /** Up close it hits with its left fist instead of shooting. */
    @Override
    protected boolean closeCombat(LivingEntity target, double distance, boolean sees) {
        if (meleeCooldown > 0) {
            meleeCooldown--;
        }
        if (distance > MELEE_REACH || !sees) {
            return false;
        }
        if (meleeCooldown <= 0) {
            meleeCooldown = 20;
            triggerAnim("action", "melee");
            swing(net.minecraft.world.InteractionHand.OFF_HAND);
            doHurtTarget(target);
            playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 1.2f);
        }
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> software.bernie.geckolib.animation.PlayState.STOP)
                .triggerableAnim("shot", SHOT)
                .triggerableAnim("melee", MELEE));
    }
}
