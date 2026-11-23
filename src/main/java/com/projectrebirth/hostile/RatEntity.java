package com.projectrebirth.hostile;

import com.projectrebirth.core.ModSounds;

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

/**
 * 1.9.0, block D: a rat of the swarms of ruins and villages (stage III and later). Small and fast, it comes in a swarm of
 * many; a bite infects the wound like any mutant's. The whole swarm goes for whoever hurts one of them. For the limits of
 * mutants a rat counts as a part of one, so a swarm is about one mutant. It keeps away from fire like the others.
 */
public class RatEntity extends MutantEntity {
    public RatEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, "rat");
        xpReward = 1;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.ATTACK_DAMAGE, 1.5)
                .add(Attributes.MOVEMENT_SPEED, 0.34).add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    public float populationWeight() {
        return 0.2f;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new LeapAtTargetGoal(this, 0.3f));
    }

    @Override
    protected void applyConfig() {
        applySpeciesConfig();
    }

    @Override
    protected double chaseSpeed() {
        return 1.25;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.RAT_SQUEAK.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.RAT_SQUEAK.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.0f + random.nextFloat() * 0.3f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SILVERFISH_STEP, 0.1f, 1.4f);
    }
}
