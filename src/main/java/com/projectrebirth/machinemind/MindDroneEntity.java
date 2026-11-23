package com.projectrebirth.machinemind;

import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.factions.FactionService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 1.9.0, block F: the work drone of a node. It puts the blocks of the plan of the base and puts back the broken ones; with nothing
 * to build (or nothing to build it of) it fells trees and takes stone and ore from the ground near the node, never on the land of
 * a faction and never from what looks built. It does not fight: hurt, or with an enemy close, it flies away.
 */
public class MindDroneEntity extends PathfinderMob implements Enemy, GeoEntity, MindUnit {
    private static final EntityDataAccessor<Boolean> WORKING = SynchedEntityData.defineId(MindDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.mind_drone.fly");
    private static final RawAnimation WORK = RawAnimation.begin().thenLoop("animation.mind_drone.work");
    /** Ticks a task may take before it is given up (or, a block at hand, done). */
    private static final int PATIENCE = 300;

    private enum Task {
        NONE, BUILD, GATHER, DELIVER, FLEE
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID node;
    @Nullable
    private BlockPos computer;
    private Task task = Task.NONE;
    @Nullable
    private MindBase.Cell cell;
    @Nullable
    private BlockPos target;
    private Vec3 away = Vec3.ZERO;
    private int taskTicks;
    private int workTicks;
    private int carried;
    private int lostTicks;

    public MindDroneEntity(EntityType<? extends MindDroneEntity> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 20, true);
        setPathfindingMalus(PathType.WATER, -1.0f);
        setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0f);
        xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.ARMOR, 4.0).add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WORKING, false);
    }

    @Override
    @Nullable
    public UUID node() {
        return node;
    }

    @Override
    public void setNode(UUID node) {
        this.node = node;
    }

    public void setComputer(BlockPos pos) {
        computer = pos.immutable();
    }

    @Override
    public void shutDown() {
        if (isAlive() && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 8, 0.2, 0.2, 0.2, 0.02);
            kill();
        }
    }

    @Nullable
    private MindComputerBlockEntity brain() {
        return computer != null && level().isLoaded(computer) && level().getBlockEntity(computer) instanceof MindComputerBlockEntity entity
                && entity.nodeId().equals(node) ? entity : null;
    }

    // ------------------------------------------------------------------ what it does

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel server = (ServerLevel) level();
        MindComputerBlockEntity brain = brain();
        if (brain == null) {
            // without its computer it hovers; the node destroyed elsewhere, it goes dead
            entityData.set(WORKING, false);
            if (++lostTicks > 100 && node != null && !MindNodes.get(server.getServer()).alive(node)) {
                shutDown();
            }
            return;
        }
        lostTicks = 0;
        if (!brain.active()) {
            entityData.set(WORKING, false);
            getNavigation().stop();
            return;
        }
        taskTicks++;
        if (task != Task.FLEE) {
            LivingEntity threat = threat();
            if (threat != null) {
                flee(threat);
            }
        }
        switch (task) {
            case BUILD -> build(server, brain);
            case GATHER -> gather(server);
            case DELIVER -> deliver(brain);
            case FLEE -> {
                if (taskTicks > 100 || taskTicks > 20 && getNavigation().isDone()) {
                    done();
                }
            }
            default -> {
                if (taskTicks >= 10) {
                    choose(server, brain);
                }
            }
        }
        entityData.set(WORKING, workTicks > 0);
    }

    private void start(Task next) {
        task = next;
        taskTicks = 0;
        workTicks = 0;
    }

    private void done() {
        cell = null;
        target = null;
        start(Task.NONE);
    }

    private void choose(ServerLevel server, MindComputerBlockEntity brain) {
        taskTicks = 0;
        cell = brain.takeJob(server, position());
        if (cell != null) {
            start(Task.BUILD);
            return;
        }
        if (brain.resources() < WastelandConfig.MIND_MAX_RESOURCES.get() && random.nextInt(2) == 0) {
            target = findResource(server, brain);
            if (target != null) {
                start(Task.GATHER);
                return;
            }
        }
        // a turn about the base
        if (getNavigation().isDone()) {
            BlockPos at = brain.getBlockPos().offset(random.nextInt(21) - 10, 2 + random.nextInt(5), random.nextInt(21) - 10);
            fly(Vec3.atCenterOf(at), 0.7);
        }
    }

    private void fly(Vec3 to, double speed) {
        getNavigation().moveTo(to.x, to.y, to.z, speed);
    }

    /** Flies to a place; true when it is at work range. */
    private boolean reach(BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        if (distanceToSqr(at) < 12.0) {
            getNavigation().stop();
            getLookControl().setLookAt(at.x, at.y, at.z);
            return true;
        }
        if (getNavigation().isDone() && taskTicks % 10 == 1) {
            fly(at.add(0.0, 1.5, 0.0), 1.0);
        }
        return false;
    }

    private void build(ServerLevel server, MindComputerBlockEntity brain) {
        if (cell == null || MindBase.built(server, cell)) {
            done();
            return;
        }
        // a place it cannot fly to (inside a wall) is built from where it is
        if (reach(cell.pos()) || taskTicks > PATIENCE) {
            workTicks++;
            sparks(server, cell.pos());
            if (workTicks >= WastelandConfig.MIND_BUILD_TICKS.get()) {
                brain.finishJob(server, cell);
                done();
            }
        }
    }

    private void gather(ServerLevel server) {
        if (target == null || !isResource(server, target) || taskTicks > PATIENCE) {
            done();
            return;
        }
        if (reach(target)) {
            workTicks++;
            sparks(server, target);
            if (workTicks >= WastelandConfig.MIND_BUILD_TICKS.get()) {
                boolean ore = server.getBlockState(target).is(Tags.Blocks.ORES);
                server.destroyBlock(target, false, this);
                carried += ore ? WastelandConfig.MIND_BLOCK_WORTH.get() * 3 : WastelandConfig.MIND_BLOCK_WORTH.get();
                start(Task.DELIVER);
            }
        }
    }

    private void deliver(MindComputerBlockEntity brain) {
        if (reach(brain.getBlockPos()) || taskTicks > PATIENCE) {
            brain.addResources(carried);
            carried = 0;
            done();
        }
    }

    private void sparks(ServerLevel server, BlockPos pos) {
        if (tickCount % 4 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
        }
        if (workTicks % 20 == 1) {
            playSound(ModSounds.MIND_DRONE_WORK.get(), 0.5f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    // ------------------------------------------------------------------ what it takes

    /** A tree or bare ground near the node; a few places are looked at each time. */
    @Nullable
    private BlockPos findResource(ServerLevel server, MindComputerBlockEntity brain) {
        if (!server.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return null;
        }
        int range = WastelandConfig.MIND_GATHER_RANGE.get();
        BlockPos middle = brain.getBlockPos();
        for (int attempt = 0; attempt < 24; attempt++) {
            int dx = random.nextInt(range * 2 + 1) - range, dz = random.nextInt(range * 2 + 1) - range;
            // the base itself stays as it is
            if (Math.max(Math.abs(dx), Math.abs(dz)) <= 18) {
                continue;
            }
            BlockPos column = middle.offset(dx, 0, dz);
            if (!server.isLoaded(column)) {
                continue;
            }
            BlockPos top = server.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
            if (isResource(server, top)) {
                return top;
            }
        }
        return null;
    }

    /** The top of a natural tree, or stone or ore lying flat in natural ground. Public for tests. */
    public static boolean isResource(ServerLevel server, BlockPos pos) {
        BlockState state = server.getBlockState(pos);
        if (FactionService.territoryAt(server.getServer(), server, pos).isPresent()) {
            return false;
        }
        if (state.is(BlockTags.LOGS)) {
            return !server.getBlockState(pos.above()).is(BlockTags.LOGS) && naturalTree(server, pos);
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.ORES)) {
            // flush with the ground: walls, towers and floors over a room are left alone
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (!ground(server.getBlockState(pos.relative(side)))) {
                    return false;
                }
            }
            return ground(server.getBlockState(pos.below())) && ground(server.getBlockState(pos.below(2)))
                    && server.getBlockState(pos.above()).getCollisionShape(server, pos.above()).isEmpty();
        }
        return false;
    }

    private static boolean ground(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(Tags.Blocks.ORES) || state.is(Tags.Blocks.GRAVELS)
                || state.is(Tags.Blocks.SANDS);
    }

    /** Natural leaves by the log, or by a log under it (a tree that is being felled keeps its crown for a while). */
    private static boolean naturalTree(ServerLevel server, BlockPos log) {
        for (BlockPos near : BlockPos.betweenClosed(log.offset(-2, -1, -2), log.offset(2, 3, 2))) {
            BlockState state = server.getBlockState(near);
            if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ danger

    @Nullable
    private LivingEntity threat() {
        LivingEntity attacker = getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && tickCount - getLastHurtByMobTimestamp() < 100 && distanceToSqr(attacker) < 576.0) {
            return attacker;
        }
        Player player = tickCount % 10 == 0 ? level().getNearestPlayer(getX(), getY(), getZ(), 5.0,
                e -> e instanceof Player p && !p.isCreative() && !p.isSpectator()) : null;
        return player;
    }

    private void flee(LivingEntity from) {
        cell = null;
        target = null;
        start(Task.FLEE);
        Vec3 direction = position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
        away = direction.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : direction.normalize();
        Vec3 to = position().add(away.scale(12.0)).add(0.0, 5.0, 0.0);
        fly(to, 1.5);
    }

    @Override
    public void die(DamageSource source) {
        MindComputerBlockEntity brain = brain();
        if (brain != null && level() instanceof ServerLevel server && brain.active()) {
            brain.delay(WastelandConfig.MIND_DRONE_DELAY.get() * 1200L);
            brain.noteDamage(server);
        }
        super.die(source);
    }

    // ------------------------------------------------------------------ the rest

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MIND_DRONE.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 60;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_DAMAGE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.6f;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (node != null) {
            tag.putUUID("node", node);
        }
        if (computer != null) {
            tag.putLong("computer", computer.asLong());
        }
        tag.putInt("carried", carried);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        node = tag.hasUUID("node") ? tag.getUUID("node") : null;
        computer = tag.contains("computer") ? BlockPos.of(tag.getLong("computer")) : null;
        carried = tag.getInt("carried");
        if (carried > 0) {
            task = Task.DELIVER;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(entityData.get(WORKING) ? WORK : FLY)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
