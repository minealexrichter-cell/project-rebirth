package com.projectrebirth.hostile;

import java.util.EnumSet;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.powerarmor.PowerArmorEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 1.9.0, block D: the screamer, a ghoul of the late stage. Weak, it keeps behind, out of reach; now and then it screams: the
 * mutants around without prey go for its prey, a human's head reels, the electronics of bodies and armor are disturbed as in
 * an EMI storm. Machines of the garrison take it first (tag projectrebirth:first_targets).
 */
public class ScreamerEntity extends MutantEntity {
    private static final String SCREAM_UNTIL = ProjectRebirth.MOD_ID + ":scream_until";
    private static final String SCREAM_LEVEL = ProjectRebirth.MOD_ID + ":scream_level";
    private static final double KEEP_MIN = 7.0;
    private static final double KEEP_MAX = 14.0;
    private int cooldown = 60;

    public ScreamerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level, "screamer");
        xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected java.util.List<String> extraAnimations() {
        return java.util.List.of("scream");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeFireGoal(this));
        goalSelector.addGoal(2, new KeepBackGoal());
        goalSelector.addGoal(3, new ScreamGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, MutantEntity::isPrey));
    }

    @Override
    protected void applyConfig() {
        applySpeciesConfig();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && cooldown > 0) {
            cooldown--;
        }
    }

    /** The scream. Public for tests; returns how many mutants it called. */
    public int scream(LivingEntity prey) {
        cooldown = RebirthConfig.SCREAMER_INTERVAL.get() * 20;
        triggerAnim("action", "scream");
        playSound(ModSounds.SCREAMER_SCREAM.get(), 3.0f, 0.9f + random.nextFloat() * 0.2f);
        double radius = RebirthConfig.SCREAMER_RADIUS.get();
        AABB around = getBoundingBox().inflate(radius);
        int called = 0;
        for (MutantEntity mutant : level().getEntitiesOfClass(MutantEntity.class, around, m -> m != this && m.isAlive())) {
            if (mutant.getTarget() == null && prey != null) {
                mutant.setTarget(prey);
                called++;
            }
        }
        long now = level().getGameTime();
        for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class, around, p -> p.isAlive() && p.gameMode.isSurvival())) {
            if (TransferService.isInArtificialBody(player) || player.getVehicle() instanceof PowerArmorEntity) {
                CompoundTag data = player.getPersistentData();
                data.putLong(SCREAM_UNTIL, now + RebirthConfig.SCREAMER_INTERFERENCE_SECONDS.get() * 20L);
                data.putFloat(SCREAM_LEVEL, RebirthConfig.SCREAMER_INTERFERENCE.get().floatValue());
            } else if (RebirthConfig.SCREAMER_NAUSEA.get() > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, RebirthConfig.SCREAMER_NAUSEA.get() * 20 + 60, 0, false, false, true));
            }
        }
        return called;
    }

    /** Interference a scream left on this player's body or armor now (added to the EMI of the place by {@link com.projectrebirth.environment.Emi}). */
    public static float interference(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.getLong(SCREAM_UNTIL) > player.level().getGameTime() ? data.getFloat(SCREAM_LEVEL) : 0.0f;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SCREAMER_MOAN.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    /** Keeps out of reach of its prey: away when it comes close, nearer when it is far. */
    private class KeepBackGoal extends Goal {
        KeepBackGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return getTarget() != null && getTarget().isAlive();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30.0f, 30.0f);
            double distance = distanceTo(target);
            if (distance < KEEP_MIN && tickCount % 10 == 0) {
                Vec3 away = DefaultRandomPos.getPosAway(ScreamerEntity.this, 10, 4, target.position());
                if (away != null) {
                    getNavigation().moveTo(away.x, away.y, away.z, 1.3);
                }
            } else if (distance > KEEP_MAX && tickCount % 20 == 0) {
                getNavigation().moveTo(target, 1.0);
            } else if (distance >= KEEP_MIN && distance <= KEEP_MAX) {
                getNavigation().stop();
            }
        }
    }

    private class ScreamGoal extends Goal {
        ScreamGoal() {
            setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return cooldown <= 0 && target != null && target.isAlive() && distanceTo(target) <= RebirthConfig.SCREAMER_RADIUS.get() && hasLineOfSight(target);
        }

        @Override
        public void start() {
            scream(getTarget());
        }
    }
}
