package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.9.0, block C: a farm animal changed by a contaminated place ({@link Mutations}): the mutated cow and pig go for whoever is
 * near like the other mutants; the mutated chicken runs from people. Their meat is contaminated (loot tables). One class,
 * the kind by the entity type; numbers in [hostile.mutants.mutated_&lt;kind&gt;].
 */
public class MutatedAnimalEntity extends MutantEntity {
    public enum Kind {
        COW("cow", true), PIG("pig", true), CHICKEN("chicken", false);

        public final String id;
        public final boolean aggressive;

        Kind(String id, boolean aggressive) {
            this.id = id;
            this.aggressive = aggressive;
        }
    }

    private final Kind kind;

    public MutatedAnimalEntity(EntityType<? extends Monster> type, Level level, Kind kind) {
        super(type, level, "mutated_" + kind.id);
        this.kind = kind;
        xpReward = kind.aggressive ? 4 : 1;
    }

    public Kind kind() {
        return kind;
    }

    public static AttributeSupplier.Builder createAttributes(double health, double damage, double speed) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ATTACK_DAMAGE, Math.max(0.0, damage))
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void registerGoals() {
        // the kind is set after the constructor of the parent runs registerGoals: decide by the type's id
        if (isSkittish()) {
            goalSelector.addGoal(0, new FloatGoal(this));
            goalSelector.addGoal(1, new FleeFireGoal(this));
            goalSelector.addGoal(2, new PanicGoal(this, 1.6));
            goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 10.0f, 1.2, 1.6));
            goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
            goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
            goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            return;
        }
        super.registerGoals();
        goalSelector.addGoal(1, new LeapAtTargetGoal(this, 0.3f));
    }

    private boolean isSkittish() {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath().equals("mutated_chicken");
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        return kind == null || kind.aggressive;
    }

    @Override
    protected void applyConfig() {
        RebirthConfig.MutatedAnimalStats stats = RebirthConfig.MUTATED_ANIMALS.get(kind.id);
        if (stats != null) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(stats.health().get());
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stats.damage().get());
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(stats.speed().get());
        }
    }

    @Override
    protected double chaseSpeed() {
        return 1.2;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return switch (kind) {
            case COW -> SoundEvents.COW_AMBIENT;
            case PIG -> SoundEvents.PIG_AMBIENT;
            case CHICKEN -> SoundEvents.CHICKEN_AMBIENT;
        };
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return switch (kind) {
            case COW -> SoundEvents.COW_HURT;
            case PIG -> SoundEvents.PIG_HURT;
            case CHICKEN -> SoundEvents.CHICKEN_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return switch (kind) {
            case COW -> SoundEvents.COW_DEATH;
            case PIG -> SoundEvents.PIG_DEATH;
            case CHICKEN -> SoundEvents.CHICKEN_DEATH;
        };
    }

    /** Lower and rougher than the animal it was. */
    @Override
    public float getVoicePitch() {
        return 0.55f + random.nextFloat() * 0.15f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(kind == Kind.CHICKEN ? SoundEvents.CHICKEN_STEP : kind == Kind.PIG ? SoundEvents.PIG_STEP : SoundEvents.COW_STEP, 0.15f, 0.8f);
    }
}
