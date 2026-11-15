package com.projectrebirth.hostile;

import com.projectrebirth.config.RebirthConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;

/**
 * The mech (1.6.0, part B3b): one heavy walker guards the military base; a boss with a health bar. A machine gun on its right
 * arm fires long bursts; the rocket pod on its left shoulder now and then marks the ground under a player with red light and an
 * alarm, and a moment later rockets fall there (they do not break blocks unless the config says so); a player who comes up
 * close is thrown back by a stomp. Its steps shake the ground. Numbers in the config ([hostile.mech]).
 */
public class MechEntity extends WalkingGunner {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mech.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.mech.walk");
    private static final RawAnimation SHOT = RawAnimation.begin().thenPlay("animation.mech.shot");
    private static final RawAnimation ROCKETS = RawAnimation.begin().thenPlay("animation.mech.rockets");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlay("animation.mech.stomp");
    private static final DustParticleOptions MARK = new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.05f), 1.6f);
    /** Ticks from the start of the stomp to the blow. */
    private static final int STOMP_WINDUP = 12;

    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private int rocketCooldown = 100;
    private int warning = -1;
    private final List<Vec3> marks = new ArrayList<>();
    private int stompCooldown;
    private int stompWindup = -1;

    public MechEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 80;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ARMOR, 15.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected boolean enabled() {
        return hostileOn() && (!RebirthConfig.SERVER_SPEC.isLoaded() || RebirthConfig.MECH_ENABLED.get());
    }

    @Override
    protected void applyConfig() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(RebirthConfig.MECH_HEALTH.get());
        getAttribute(Attributes.ARMOR).setBaseValue(RebirthConfig.MECH_ARMOR.get());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RebirthConfig.MECH_SPEED.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(RebirthConfig.MECH_STOMP_DAMAGE.get());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(RebirthConfig.MECH_RANGE.get() + 8.0);
    }

    @Override
    protected double range() {
        return RebirthConfig.MECH_RANGE.get();
    }

    @Override
    protected int postRadius() {
        return RebirthConfig.MECH_RADIUS.get();
    }

    @Override
    protected double spread() {
        return RebirthConfig.MECH_SPREAD.get();
    }

    @Override
    protected float bulletDamage() {
        return RebirthConfig.MECH_DAMAGE.get().floatValue();
    }

    @Override
    protected int burst() {
        return RebirthConfig.MECH_BURST.get();
    }

    @Override
    protected int shotInterval() {
        return RebirthConfig.MECH_SHOT_INTERVAL.get();
    }

    @Override
    protected int burstPause() {
        return RebirthConfig.MECH_BURST_PAUSE.get();
    }

    @Override
    protected int lockTicks() {
        return 20;
    }

    @Override
    protected double shootingDistance() {
        return range() * 0.5;
    }

    /** The machine gun under its right arm. */
    @Override
    protected Vec3 muzzle() {
        return position().add(0.0, 2.22, 0.0).add(right().scale(0.9)).add(forward().scale(1.8));
    }

    /** The rocket pod on its left shoulder. */
    private Vec3 pod() {
        return position().add(0.0, 3.05, 0.0).add(right().scale(-1.15)).add(forward().scale(-0.4));
    }

    @Override
    protected float shotPitch() {
        return 0.7f;
    }

    @Override
    protected void onShot() {
        triggerAnim("action", "shot");
    }

    @Override
    protected boolean closeCombat(LivingEntity target, double distance, boolean sees) {
        if (stompCooldown > 0) {
            stompCooldown--;
        }
        if (stompWindup >= 0) {
            if (--stompWindup < 0) {
                stomp();
            }
            return true;
        }
        if (distance <= RebirthConfig.MECH_STOMP_RADIUS.get() + getBbWidth() * 0.5 && stompCooldown <= 0) {
            stompWindup = STOMP_WINDUP;
            stompCooldown = 60;
            triggerAnim("action", "stomp");
            playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.5f, 0.5f);
            return true;
        }
        rockets(target, sees);
        return false;
    }

    /** The salvo: marks with a warning first, then the rockets, which fly whatever the target does. */
    private void rockets(LivingEntity target, boolean sees) {
        if (warning >= 0) {
            if (warning % 4 == 0 && level() instanceof ServerLevel server) {
                for (Vec3 mark : marks) {
                    ring(server, mark);
                }
                server.playSound(null, getX(), getY() + 3.0, getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 2.0f, 2.0f);
            }
            if (--warning < 0) {
                launch();
            }
            return;
        }
        if (rocketCooldown > 0) {
            rocketCooldown--;
            return;
        }
        if (!sees) {
            return;
        }
        marks.clear();
        Vec3 at = ground(target.position());
        marks.add(at);
        for (int i = 1; i < RebirthConfig.MECH_ROCKETS.get(); i++) {
            double a = getRandom().nextDouble() * Math.PI * 2.0, r = 1.5 + getRandom().nextDouble() * 2.0;
            marks.add(ground(at.add(Math.cos(a) * r, 0.0, Math.sin(a) * r)));
        }
        warning = RebirthConfig.MECH_ROCKET_WARNING.get();
        rocketCooldown = RebirthConfig.MECH_ROCKET_INTERVAL.get();
        playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 3.0f, 0.6f);
    }

    /** The top of the ground under a point: where the rocket will land. */
    private Vec3 ground(Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        for (int i = 0; i < 6 && level().getBlockState(pos.below()).getCollisionShape(level(), pos.below()).isEmpty(); i++) {
            pos = pos.below();
        }
        return new Vec3(point.x, pos.getY() + 0.05, point.z);
    }

    private static void ring(ServerLevel server, Vec3 mark) {
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            server.sendParticles(MARK, mark.x + Math.cos(a) * 1.4, mark.y + 0.1, mark.z + Math.sin(a) * 1.4, 1, 0.0, 0.0, 0.0, 0.0);
        }
        server.sendParticles(MARK, mark.x, mark.y + 0.1, mark.z, 4, 0.3, 0.0, 0.3, 0.0);
    }

    private void launch() {
        triggerAnim("action", "rockets");
        Vec3 from = pod();
        for (int i = 0; i < marks.size(); i++) {
            MechRocketEntity rocket = new MechRocketEntity(level(), this, from.add(0.0, 0.0, 0.0), marks.get(i), 22 + i * 5);
            level().addFreshEntity(rocket);
        }
        playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0f, 0.6f);
        marks.clear();
    }

    /** The stomp: the players close by are hurt and thrown away from it. */
    private void stomp() {
        double radius = RebirthConfig.MECH_STOMP_RADIUS.get() + getBbWidth() * 0.5;
        Vec3 centre = position().add(forward().scale(0.6));
        float damage = RebirthConfig.MECH_STOMP_DAMAGE.get().floatValue();
        for (Player player : level().getEntitiesOfClass(Player.class, new AABB(centre, centre).inflate(radius, 2.0, radius), Gunnery::fair)) {
            if (player.distanceToSqr(centre) > radius * radius + 2.0) {
                continue;
            }
            player.hurt(damageSources().mobAttack(this), damage);
            Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0e-4 ? forward() : away.normalize();
            player.push(away.x * 1.4, 0.55, away.z * 1.4);
            player.hurtMarked = true;
        }
        if (level() instanceof ServerLevel server) {
            BlockState under = level().getBlockState(blockPosition().below());
            if (under.isAir()) {
                under = Blocks.STONE.defaultBlockState();
            }
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), centre.x, getY() + 0.1, centre.z, 60, radius * 0.5, 0.1, radius * 0.5, 0.2);
            server.sendParticles(ParticleTypes.EXPLOSION, centre.x, getY() + 0.3, centre.z, 2, 0.6, 0.0, 0.6, 0.0);
        }
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 2.0f, 0.5f);
        playSound(SoundEvents.ANVIL_LAND, 0.25f, 0.5f);
    }

    @Override
    protected float nextStep() {
        return moveDist + 1.6f; // long, heavy steps
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0f, 1.0f));
        if (getTarget() == null) {
            warning = -1;
            stompWindup = -1;
            marks.clear();
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    /** Killed: it bursts in smoke and sparks. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2.0, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.0, getZ(), 40, 1.0, 1.2, 1.0, 0.03);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.5, getZ(), 30, 1.0, 1.0, 1.0, 0.3);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.7f);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("shot", SHOT)
                .triggerableAnim("rockets", ROCKETS)
                .triggerableAnim("stomp", STOMP));
    }
}
