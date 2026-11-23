package com.projectrebirth.hostile;

import java.util.EnumSet;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * 1.9.0, block D: the crawler, a pale thing of the deep caves. It moves under the ground, only a hump of its back above it
 * and the rock cracking where it goes; under its prey it bursts out and strikes harder than it can above ground, fights for a
 * few seconds and burrows again. Under the open sky it is weak: slower and it takes more damage. It burrows only into
 * earth and stone.
 */
public class CrawlerEntity extends MutantEntity {
    private static final EntityDataAccessor<Boolean> BURROWED = SynchedEntityData.defineId(CrawlerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double EMERGE_DISTANCE = 2.5;
    private final RawAnimation burrowed = RawAnimation.begin().thenLoop("animation.crawler.burrowed");
    /** Ticks it has spent above ground since it last came out. */
    private int surfaced;

    public CrawlerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, "crawler");
        xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BURROWED, false);
    }

    public boolean isBurrowed() {
        return entityData.get(BURROWED);
    }

    /** Public for tests. */
    public void setBurrowed(boolean value) {
        if (value != isBurrowed()) {
            entityData.set(BURROWED, value);
            triggerAnim("action", value ? "burrow" : "emerge");
            playSound(value ? SoundEvents.ROOTED_DIRT_BREAK : SoundEvents.GRAVEL_BREAK, 1.0f, 0.6f);
        }
        surfaced = 0;
    }

    @Override
    protected java.util.List<String> extraAnimations() {
        return java.util.List.of("burrow", "emerge");
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        return isBurrowed() ? burrowed : super.mainAnimation(moving);
    }

    /** Whether it can go under the ground here: earth or stone under it, no open sky. Public for tests. */
    public boolean canBurrowHere() {
        BlockState below = level().getBlockState(blockPosition().below());
        return !level().canSeeSky(blockPosition()) && (below.is(BlockTags.BASE_STONE_OVERWORLD) || below.is(BlockTags.DIRT)
                || below.is(BlockTags.MINEABLE_WITH_SHOVEL) || below.is(net.minecraft.world.level.block.Blocks.COBBLESTONE));
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new EmergeGoal());
    }

    @Override
    protected void applyConfig() {
        applySpeciesConfig();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // the rock cracking where it moves under the ground
            if (isBurrowed() && (getDeltaMovement().horizontalDistanceSqr() > 1.0e-4 || tickCount % 10 == 0)) {
                BlockState below = level().getBlockState(blockPosition().below());
                if (!below.isAir()) {
                    level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below), getX() + (random.nextDouble() - 0.5) * 0.8,
                            getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.1, 0.0);
                }
            }
            return;
        }
        if (!isBurrowed()) {
            surfaced++;
            if (surfaced > RebirthConfig.CRAWLER_SURFACE_SECONDS.get() * 20 && canBurrowHere()
                    && (getTarget() == null || distanceTo(getTarget()) > EMERGE_DISTANCE + 1.5)) {
                setBurrowed(true);
            }
        } else if (!canBurrowHere()) {
            setBurrowed(false);
        }
        boolean open = level().canSeeSky(blockPosition());
        var slow = getAttribute(Attributes.MOVEMENT_SPEED);
        var id = com.projectrebirth.ProjectRebirth.id("crawler_open");
        if (open && slow.getModifier(id) == null) {
            slow.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, -0.35,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!open && slow.getModifier(id) != null) {
            slow.removeModifier(id);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // under the ground it is hard to reach; under the open sky it is weak
        if (isBurrowed()) {
            amount *= 0.5f;
        } else if (level().canSeeSky(blockPosition())) {
            amount *= RebirthConfig.CRAWLER_OPEN_DAMAGE.get().floatValue();
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isBurrowed()) {
            return false;
        }
        return super.doHurtTarget(target);
    }

    /** Bursts out under its prey: up into the air and a blow stronger than its own. Public for tests. */
    public boolean emergeAt(LivingEntity target) {
        setBurrowed(false);
        setDeltaMovement(getDeltaMovement().add(0.0, 0.55, 0.0));
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * RebirthConfig.CRAWLER_AMBUSH.get().floatValue();
        boolean hit = target.hurt(damageSources().mobAttack(this), damage);
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            BlockState below = level().getBlockState(blockPosition().below());
            if (!below.isAir()) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), getX(), getY() + 0.3, getZ(), 30, 0.5, 0.3, 0.5, 0.15);
            }
        }
        return hit;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("burrowed", isBurrowed());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(BURROWED, tag.getBoolean("burrowed"));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isBurrowed() ? null : com.projectrebirth.core.ModSounds.CRAWLER_CHITTER.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5f + random.nextFloat() * 0.1f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (!isBurrowed()) {
            playSound(SoundEvents.SPIDER_STEP, 0.15f, 0.7f);
        }
    }

    /** Under the ground near its prey: out with a blow. */
    private class EmergeGoal extends Goal {
        EmergeGoal() {
            setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return isBurrowed() && target != null && target.isAlive() && distanceTo(target) <= EMERGE_DISTANCE;
        }

        @Override
        public void start() {
            LivingEntity target = getTarget();
            if (target != null) {
                emergeAt(target);
            }
        }
    }
}
