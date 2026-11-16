package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** The mutated hound (1.6.0, part B3c): fast and weak, it comes in packs; hurt one and the pack turns on you. */
public class MutatedHoundEntity extends MutantEntity {
    public MutatedHoundEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, "mutated_hound");
        xpReward = 4;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new LeapAtTargetGoal(this, 0.4f));
    }

    /** The pack hunts together: when one picks a player, the hounds about that have nobody go for the same one. */
    @Override
    public void setTarget(net.minecraft.world.entity.LivingEntity target) {
        boolean fresh = target != null && target != getTarget();
        super.setTarget(target);
        if (fresh && !level().isClientSide && target instanceof net.minecraft.world.entity.player.Player) {
            for (MutatedHoundEntity other : level().getEntitiesOfClass(MutatedHoundEntity.class, getBoundingBox().inflate(24.0),
                    h -> h != this && h.isAlive() && h.getTarget() == null)) {
                other.setTarget(target);
            }
            playSound(SoundEvents.WOLF_HOWL, 1.2f, 0.7f);
        }
    }

    @Override
    protected void applyConfig() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(RebirthConfig.HOUND_HEALTH.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(RebirthConfig.HOUND_DAMAGE.get());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RebirthConfig.HOUND_SPEED.get());
    }

    @Override
    protected double chaseSpeed() {
        return 1.15;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7f + random.nextFloat() * 0.15f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.15f, 0.8f);
    }
}
