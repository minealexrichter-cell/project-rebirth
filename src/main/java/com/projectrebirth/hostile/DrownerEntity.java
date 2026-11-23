package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * 1.9.0, block D: the drowner of contaminated water. It swims well under water and drags a swimmer it strikes down to the
 * bottom for a few seconds; from below it strikes a boat until it breaks. On land it is slow.
 */
public class DrownerEntity extends MutantEntity {
    private final RawAnimation swim = RawAnimation.begin().thenLoop("animation.drowner.swim");
    private LivingEntity dragged;
    private int dragTicks;

    public DrownerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, "drowner");
        xpReward = 8;
        moveControl = new SwimControl();
        setPathfindingMalus(PathType.WATER, 0.0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    /** It lives in water: no swimming up by jumps (FloatGoal); it wanders under water, on land as the others. */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.removeAllGoals(goal -> goal instanceof net.minecraft.world.entity.ai.goal.FloatGoal
                || goal instanceof net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal);
        goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.RandomSwimmingGoal(this, 1.0, 40));
        goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return !isInWater() && super.canUse();
            }
        });
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return !isSwimming();
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        return isInWater() ? swim : super.mainAnimation(moving);
    }

    @Override
    protected void applyConfig() {
        applySpeciesConfig();
    }

    /** Under water it swims after its target, in all three directions. */
    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(0.02f, input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
            Vec3 motion = getDeltaMovement();
            if (horizontalCollision && getTarget() != null && isFree(motion.x, motion.y + 0.6, motion.z)) {
                // out onto a bank after its prey, as every swimmer climbs
                setDeltaMovement(motion.x, 0.3, motion.z);
            } else if (!isUnderWater() && motion.y > 0.0) {
                // at the surface it does not leap out
                setDeltaMovement(motion.x, 0.0, motion.z);
            }
        } else {
            super.travel(input);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living && living.isInWater()) {
            dragged = living;
            dragTicks = RebirthConfig.DROWNER_DRAG_SECONDS.get() * 20;
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        // drags the one it struck down to the bottom
        if (dragTicks > 0) {
            dragTicks--;
            if (dragged != null && dragged.isAlive() && dragged.isInWater() && distanceTo(dragged) < 3.0) {
                dragged.setDeltaMovement(dragged.getDeltaMovement().add(0.0, -RebirthConfig.DROWNER_DRAG.get(), 0.0));
                dragged.hurtMarked = true;
            }
        }
        // a boat over it is struck from below
        LivingEntity target = getTarget();
        if (tickCount % 20 == 0 && target != null && target.getVehicle() instanceof Boat boat && isInWater()
                && getY() < boat.getY() && boat.distanceTo(this) < 3.5) {
            strikeBoat(boat);
        }
    }

    /** A blow from below. Public for tests. */
    public void strikeBoat(Boat boat) {
        triggerAnim("action", "attack");
        boat.hurt(damageSources().mobAttack(this), RebirthConfig.DROWNER_BOAT_DAMAGE.get().floatValue());
        playSound(SoundEvents.WOOD_BREAK, 1.0f, 0.8f);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.DROWNER_GURGLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isInWater() ? SoundEvents.DROWNED_HURT_WATER : SoundEvents.DROWNED_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return isInWater() ? SoundEvents.DROWNED_DEATH_WATER : SoundEvents.DROWNED_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f + random.nextFloat() * 0.1f;
    }

    /** Swimming toward where it wants to be, up and down too; on land the ordinary walk. */
    private class SwimControl extends MoveControl {
        SwimControl() {
            super(DrownerEntity.this);
        }

        @Override
        public void tick() {
            if (!mob.isInWater()) {
                super.tick();
                return;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && target.getY() > mob.getY()) {
                mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, 0.002, 0.0));
            }
            if (operation != Operation.MOVE_TO || mob.getNavigation().isDone()) {
                mob.setSpeed(0.0f);
                return;
            }
            double dx = wantedX - mob.getX(), dy = wantedY - mob.getY(), dz = wantedZ - mob.getZ();
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0e-4) {
                return;
            }
            dy /= length;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
            mob.setYRot(rotlerp(mob.getYRot(), yaw, 90.0f));
            mob.yBodyRot = mob.getYRot();
            // in water it is three times as fast as it walks
            float speed = (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)) * 3.0f;
            float now = Mth.lerp(0.125f, mob.getSpeed(), speed);
            mob.setSpeed(now);
            mob.setDeltaMovement(mob.getDeltaMovement().add(now * dx * 0.005, now * dy * 0.1, now * dz * 0.005));
        }
    }
}
