package com.projectrebirth.hostile;

import java.util.EnumSet;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentState;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * 1.9.0, block D: the hunter. At night and in a toxic or dust storm, away from light, it is almost invisible; it creeps up and
 * leaps. A blow or its own attack shows it for a while. Strong light (a floodlight, lamps) drives it back.
 */
public class HunterEntity extends MutantEntity {
    private static final EntityDataAccessor<Boolean> CLOAKED = SynchedEntityData.defineId(HunterEntity.class, EntityDataSerializers.BOOLEAN);
    /** Ticks it stays seen after a blow given or taken. */
    private static final int SHOWN_TICKS = 60;
    private int shown;

    public HunterEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, "hunter");
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26.0).add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CLOAKED, false);
    }

    public boolean isCloaked() {
        return entityData.get(CLOAKED);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FleeLightGoal());
        goalSelector.addGoal(1, new LeapAtTargetGoal(this, 0.5f));
    }

    @Override
    protected void applyConfig() {
        applySpeciesConfig();
    }

    @Override
    protected double chaseSpeed() {
        return 1.1;
    }

    /** Whether it is hidden here and now: night, a fog or dust storm, little light, and not shown by a fight. Public for tests. */
    public boolean wouldCloak() {
        if (shown > 0 || !(level() instanceof ServerLevel server)) {
            return false;
        }
        boolean dark = server.dimensionType().hasSkyLight() && server.isNight();
        EnvironmentState state = EnvironmentState.get(server);
        boolean storm = state.strengthNow() > 0.0f && (Environment.TOXIC_STORM.equals(state.event()) || Environment.DUST_STORM.equals(state.event()));
        return (dark || storm) && level().getBrightness(LightLayer.BLOCK, blockPosition()) < RebirthConfig.HUNTER_CLOAK_LIGHT.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (shown > 0) {
                shown--;
            }
            if (tickCount % 10 == 0 || shown == SHOWN_TICKS - 1) {
                boolean cloak = wouldCloak();
                if (cloak != isCloaked()) {
                    entityData.set(CLOAKED, cloak);
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            reveal();
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        reveal();
        return super.doHurtTarget(target);
    }

    private void reveal() {
        shown = SHOWN_TICKS;
        if (isCloaked()) {
            entityData.set(CLOAKED, false);
        }
    }

    /** Whether the light here drives it off. Public for tests. */
    public boolean inStrongLight() {
        return level().getBrightness(LightLayer.BLOCK, blockPosition()) >= RebirthConfig.HUNTER_FLEE_LIGHT.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isCloaked() ? null : ModSounds.HUNTER_GROWL.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.3f + random.nextFloat() * 0.2f;
    }

    /** Away from strong light, out of it. */
    private class FleeLightGoal extends Goal {
        private Vec3 away;

        FleeLightGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (tickCount % 10 != 0 || !inStrongLight()) {
                return false;
            }
            LivingEntity target = getTarget();
            away = DefaultRandomPos.getPosAway(HunterEntity.this, 12, 5, target != null ? target.position() : position());
            return away != null;
        }

        @Override
        public void start() {
            getNavigation().moveTo(away.x, away.y, away.z, 1.4);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone();
        }
    }
}
