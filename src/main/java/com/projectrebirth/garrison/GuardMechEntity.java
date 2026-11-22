package com.projectrebirth.garrison;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.hostile.Gunnery;
import com.projectrebirth.hostile.MechRocketEntity;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.items.IItemHandler;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The guard mech of the player (1.7.0, block H; the concept's section 5.5): the mech of the ruins in service, without its second
 * phase, under an AI core. Two weapon nodes, one on each side (a machine gun node or a rocket launcher node, in any mix; swapped by
 * right-clicking it with a node: the right side, with Shift the left), its own energy (FE) and magazine (assault rifle rounds and
 * rockets, loaded by hand or at a service post). It follows the console's orders (guard, patrol, escort, hold, to the post, take
 * cover, stand down), fights by the rules of targets and goes to a service post when its energy runs low; empty, it stops and the
 * console hears of it. Nobody pilots it. Integrity 250 / 125 and three upgrade slots ([garrison.machines.mech]).
 */
public class GuardMechEntity extends MachineEntity implements GeoEntity {
    public static final int RIGHT = 0;
    public static final int LEFT = 1;
    private static final EntityDataAccessor<Integer> RIGHT_NODE = SynchedEntityData.defineId(GuardMechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEFT_NODE = SynchedEntityData.defineId(GuardMechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(GuardMechEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOWN = SynchedEntityData.defineId(GuardMechEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(GuardMechEntity.class, EntityDataSerializers.BOOLEAN);
    /** 1.7.2: ticks from the start of the stomp to the blow (the leg comes down at 0.6 s of the animation). */
    private static final int STOMP_WINDUP = 12;

    /** What sits on a side: nothing, a machine gun node, a rocket launcher node. */
    public enum Node {
        NONE, GUN, ROCKET;

        public static Node of(ItemStack stack) {
            return stack.is(GarrisonRegistry.MACHINE_GUN_NODE.get()) ? GUN : stack.is(GarrisonRegistry.ROCKET_LAUNCHER_NODE.get()) ? ROCKET : NONE;
        }

        public static Node byId(int id) {
            Node[] values = values();
            return id >= 0 && id < values.length ? values[id] : NONE;
        }

        public ItemStack stack() {
            return switch (this) {
                case GUN -> new ItemStack(GarrisonRegistry.MACHINE_GUN_NODE.get());
                case ROCKET -> new ItemStack(GarrisonRegistry.ROCKET_LAUNCHER_NODE.get());
                default -> ItemStack.EMPTY;
            };
        }
    }

    /**
     * What a packed mech keeps: its nodes, energy and magazine. An energy of -1 is a mech fresh from the dock (1.7.1): it starts
     * fully charged, else it would shut down at once and stand deaf to orders until it found a service post.
     */
    public record Loadout(int right, int left, int energy, int rounds, int rockets) {
        public static final int FULL = -1;
        public static final Loadout DEFAULT = new Loadout(Node.GUN.ordinal(), Node.ROCKET.ordinal(), FULL, 0, 0);
        public static final Codec<Loadout> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("right", 1).forGetter(Loadout::right),
                Codec.INT.optionalFieldOf("left", 2).forGetter(Loadout::left),
                Codec.INT.optionalFieldOf("energy", FULL).forGetter(Loadout::energy),
                Codec.INT.optionalFieldOf("rounds", 0).forGetter(Loadout::rounds),
                Codec.INT.optionalFieldOf("rockets", 0).forGetter(Loadout::rockets)).apply(i, Loadout::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Loadout> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float energy;
    private int rounds;
    private int rockets;
    private boolean emergency;
    private boolean lacking;
    private final int[] cooldown = new int[2];
    private final int[] shotsLeft = new int[2];
    private int lockTicks;
    private long targetSince;
    @Nullable
    private LivingEntity lastTarget;
    @Nullable
    private BlockPos holdPoint;
    private MachineTask lastTask = MachineTask.STAND_DOWN;
    private int routeIndex;
    /** 1.7.2: how it gets to a place (paths, straight ways, getting unstuck). */
    protected final MachineMover mover = new MachineMover(this);
    /** 1.8.0, block I: its tactics between ticks. */
    protected final com.projectrebirth.garrison.tactics.TacticsState tactics = new com.projectrebirth.garrison.tactics.TacticsState();
    private boolean stuckReported;
    private boolean noPostReported;
    /** 1.7.2: where it was when it set out for the post; with no order it walks back there, off the plates, after it was served. */
    @Nullable
    private Vec3 cameFrom;
    /** 1.7.2: the game time before which it does not go to the post for ammunition again (the post had none). */
    private long ammoRetry;
    /** 1.7.2, block C: its jetpack (the small robot has none), and the stomp. */
    private final MechJetpack jetpack = new MechJetpack(this);
    /** 1.7.2, block D: its walk in the order "area patrol". */
    private final AreaPatrol areaPatrol = new AreaPatrol(this, mover);

    private int stompCooldown;
    private int stompWindup = -1;

    public GuardMechEntity(EntityType<? extends GuardMechEntity> type, Level level) {
        this(type, level, MachineKind.MECH);
    }

    /** 1.7.1: a machine of another kind on the mech's logic (the small robot), with its own numbers from {@link #stats()}. */
    protected GuardMechEntity(EntityType<? extends GuardMechEntity> type, Level level, MachineKind kind) {
        super(type, level, kind);
        mover.setLift(jetpack::hop);
    }

    // ------------------------------------------------------------------ 1.7.2: the jetpack and the stomp

    /** Whether it has a jetpack and may use it (the guard mech; not the small robot). */
    public boolean canFly() {
        return RebirthConfig.GUARD_MECH_JETPACK.get();
    }

    /** Whether it stomps whoever comes too close (the guard mech; not the small robot). */
    public boolean canStomp() {
        return true;
    }

    /** Client and server: in the air on its jetpack. */
    public boolean flying() {
        return entityData.get(FLYING);
    }

    void setFlyingShown(boolean flying) {
        entityData.set(FLYING, flying);
    }

    /** Public for tests. */
    public boolean jetpackReady() {
        return jetpack.ready();
    }

    /** A hop over an obstacle towards a place; whether it took off. Public for tests. */
    public boolean hopTowards(Vec3 goal) {
        return jetpack.hop(goal);
    }

    double lowEnergyShare() {
        return stats().lowEnergy().get();
    }

    void useEnergy(float amount) {
        energy = Math.max(0.0f, energy - amount);
    }

    private Vec3 forward() {
        double yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    /** Where the flame of a nozzle of its jetpack comes out, in the world (side -1 right, 1 left). */
    Vec3 jetNozzle(int side) {
        double yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        return position().add(0.0, 1.85, 0.0).add(forward().scale(-0.85)).add(right.scale(-side * 0.37));
    }

    /**
     * The stomp (up close, and when it lands from a flight): whoever it may attack by its rules of targets within reach of its feet
     * is hurt and thrown away; its own, its allies and its faction's players are not touched. Public for tests.
     */
    public void stomp() {
        if (!canStomp()) {
            return;
        }
        double radius = RebirthConfig.GUARD_MECH_STOMP_RADIUS.get() + getBbWidth() * 0.5;
        float damage = RebirthConfig.GUARD_MECH_STOMP_DAMAGE.get().floatValue();
        for (LivingEntity hit : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 1.0, radius),
                e -> e != this && e.isAlive() && TargetRules.mayAttack(this, e))) {
            if (hit.position().subtract(position()).horizontalDistanceSqr() > radius * radius) {
                continue;
            }
            hit.hurt(damageSources().mobAttack(this), damage);
            Vec3 away = hit.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0e-4 ? forward() : away.normalize();
            hit.push(away.x * 1.2, 0.5, away.z * 1.2);
            hit.hurtMarked = true;
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            net.minecraft.world.level.block.state.BlockState under = level().getBlockState(blockPosition().below());
            if (under.isAir()) {
                under = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            }
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, under),
                    getX(), getY() + 0.1, getZ(), 50, radius * 0.5, 0.1, radius * 0.5, 0.2);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.6, 0.0, 0.6, 0.0);
        }
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f);
        stompCooldown = RebirthConfig.GUARD_MECH_STOMP_COOLDOWN.get();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    /** 1.7.1: a walking machine is solid, as the hostile robots and the power armor: one bumps into it and it is not pushed about. */
    @Override
    public boolean canBeCollidedWith() {
        return isAlive();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    /**
     * 1.7.1: the numbers of a walking machine on this logic, from its section of the config. The mech has [garrison.mech]; the small
     * robot (a subclass) has its own.
     */
    public record Stats(ModConfigSpec.BooleanValue enabled, ModConfigSpec.DoubleValue speed, ModConfigSpec.IntValue capacity,
                        ModConfigSpec.DoubleValue energyIdle, ModConfigSpec.DoubleValue energyMoving, ModConfigSpec.IntValue energyShot,
                        ModConfigSpec.IntValue rounds, ModConfigSpec.IntValue rockets, ModConfigSpec.IntValue range, ModConfigSpec.DoubleValue damage,
                        ModConfigSpec.DoubleValue spread, ModConfigSpec.IntValue burst, ModConfigSpec.IntValue shotTicks, ModConfigSpec.IntValue pauseTicks,
                        ModConfigSpec.IntValue rocketTicks, ModConfigSpec.DoubleValue lowEnergy, ModConfigSpec.DoubleValue charged,
                        ModConfigSpec.IntValue guardRadius) {
    }

    @Nullable
    private static Stats mechStats;

    protected Stats stats() {
        if (mechStats == null) {
            mechStats = new Stats(RebirthConfig.MECHS_ENABLED, RebirthConfig.GUARD_MECH_SPEED, RebirthConfig.GUARD_MECH_ENERGY_CAPACITY,
                    RebirthConfig.GUARD_MECH_ENERGY_IDLE, RebirthConfig.GUARD_MECH_ENERGY_MOVING, RebirthConfig.GUARD_MECH_ENERGY_SHOT,
                    RebirthConfig.GUARD_MECH_ROUNDS, RebirthConfig.GUARD_MECH_ROCKETS, RebirthConfig.GUARD_MECH_RANGE, RebirthConfig.GUARD_MECH_DAMAGE,
                    RebirthConfig.GUARD_MECH_SPREAD, RebirthConfig.GUARD_MECH_BURST, RebirthConfig.GUARD_MECH_SHOT_TICKS, RebirthConfig.GUARD_MECH_PAUSE_TICKS,
                    RebirthConfig.GUARD_MECH_ROCKET_TICKS, RebirthConfig.GUARD_MECH_LOW_ENERGY, RebirthConfig.GUARD_MECH_CHARGED,
                    RebirthConfig.GUARD_MECH_GUARD_RADIUS);
        }
        return mechStats;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected Item packedItem() {
        return GarrisonRegistry.GUARD_MECH_ITEM.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RIGHT_NODE, Node.GUN.ordinal());
        builder.define(LEFT_NODE, Node.ROCKET.ordinal());
        builder.define(AIMING, false);
        builder.define(DOWN, false);
        builder.define(FLYING, false);
    }

    // ------------------------------------------------------------------ nodes, energy, magazine

    public Node node(int side) {
        return Node.byId(entityData.get(side == RIGHT ? RIGHT_NODE : LEFT_NODE));
    }

    public void setNode(int side, Node node) {
        entityData.set(side == RIGHT ? RIGHT_NODE : LEFT_NODE, node.ordinal());
    }

    public boolean aiming() {
        return entityData.get(AIMING);
    }

    /** Client and server: shut down, a wreck or out of energy (it kneels). */
    public boolean down() {
        return level().isClientSide ? entityData.get(DOWN) || shownState() != MachineCore.State.ACTIVE : !working();
    }

    public float energy() {
        return energy;
    }

    public int capacity() {
        return stats().capacity().get();
    }

    public int rounds() {
        return rounds;
    }

    @Override
    public float consoleEnergy() {
        return capacity() <= 0 ? -1.0f : Mth.clamp(energy / capacity(), 0.0f, 1.0f);
    }

    @Override
    public int consoleAmmo() {
        return rounds + rockets;
    }

    public int rockets() {
        return rockets;
    }

    /** Takes up to that much energy; returns how much it took. */
    public int addEnergy(int amount) {
        int taken = (int) Math.max(0, Math.min(amount, capacity() - energy));
        energy += taken;
        return taken;
    }

    /** For tests and commands. */
    public void setEnergy(float value) {
        energy = Mth.clamp(value, 0.0f, capacity());
    }

    /** Fills its magazine from a store: rounds for a machine gun node, rockets for a rocket node. */
    public void refill(IItemHandler store) {
        for (int slot = 0; slot < store.getSlots(); slot++) {
            ItemStack stack = store.getStackInSlot(slot);
            int room = stack.is(WeaponRegistry.ASSAULT_RIFLE_AMMO.get()) ? stats().rounds().get() - rounds
                    : stack.is(GarrisonRegistry.ROCKET.get()) ? stats().rockets().get() - rockets : 0;
            if (room > 0) {
                ItemStack taken = store.extractItem(slot, room, false);
                load(taken);
            }
        }
    }

    /** Puts rounds or rockets into its magazine as far as there is room; what does not fit stays in the stack. */
    public void load(ItemStack stack) {
        if (stack.is(WeaponRegistry.ASSAULT_RIFLE_AMMO.get())) {
            int put = Math.min(stack.getCount(), stats().rounds().get() - rounds);
            rounds += put;
            stack.shrink(put);
        } else if (stack.is(GarrisonRegistry.ROCKET.get())) {
            int put = Math.min(stack.getCount(), stats().rockets().get() - rockets);
            rockets += put;
            stack.shrink(put);
        }
    }

    public boolean working() {
        return machineCore().active() && !emergency && stats().enabled().get() && RebirthConfig.GARRISON_ENABLED.get();
    }

    // ------------------------------------------------------------------ packing

    @Override
    protected void writePacked(ItemStack stack) {
        stack.set(GarrisonRegistry.MECH_LOADOUT.get(), new Loadout(node(RIGHT).ordinal(), node(LEFT).ordinal(), (int) energy, rounds, rockets));
    }

    @Override
    public void readPacked(ItemStack stack) {
        Loadout loadout = stack.getOrDefault(GarrisonRegistry.MECH_LOADOUT.get(), Loadout.DEFAULT);
        setNode(RIGHT, Node.byId(loadout.right()));
        setNode(LEFT, Node.byId(loadout.left()));
        energy = loadout.energy() == Loadout.FULL ? capacity() : Mth.clamp(loadout.energy(), 0, capacity());
        rounds = loadout.rounds();
        rockets = loadout.rockets();
    }

    // ------------------------------------------------------------------ hands

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Node node = swapsNodes() ? Node.of(held) : Node.NONE;
        boolean ammo = held.is(WeaponRegistry.ASSAULT_RIFLE_AMMO.get()) || held.is(GarrisonRegistry.ROCKET.get());
        boolean empty = held.isEmpty() && hand == InteractionHand.MAIN_HAND;
        if (node == Node.NONE && !ammo && !empty) {
            return super.mobInteract(player, hand);
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        MachineCore core = machineCore();
        if (core.wreck()) {
            say(player, "wreck");
        } else if (!core.mayManage(player)) {
            say(player, "not_yours");
        } else if (node != Node.NONE) {
            int side = player.isShiftKeyDown() ? LEFT : RIGHT;
            ItemStack old = node(side).stack();
            setNode(side, node);
            held.consume(1, player);
            if (!old.isEmpty() && !player.getInventory().add(old)) {
                player.drop(old, false);
            }
            playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0f, 0.8f);
            say(player, "mech_node", Component.translatable("mech_side.projectrebirth." + (side == RIGHT ? "right" : "left")), node.stack().getHoverName());
        } else if (ammo) {
            ItemStack loading = held.copy();
            load(loading);
            if (!player.isCreative()) {
                held.setCount(loading.getCount());
            }
            playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0f, 1.0f);
            status(player);
        } else {
            status(player);
        }
        return InteractionResult.CONSUME;
    }

    private void status(Player player) {
        say(player, "mech_status", (int) energy, capacity(), rounds, rockets);
    }

    // ------------------------------------------------------------------ time

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        for (int side = 0; side < 2; side++) {
            if (cooldown[side] > 0) {
                cooldown[side]--;
            }
        }
        jetpack.tick();
        if (stompCooldown > 0) {
            stompCooldown--;
        }
        if (stompWindup >= 0 && --stompWindup < 0 && working() && onGround()) {
            stomp();
        }
        if (tickCount % 20 == 0) {
            second();
        }
    }

    private void second() {
        MachineCore core = machineCore();
        if (core.active()) {
            boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0e-4;
            energy = Math.max(0.0f, energy - (float) (stats().energyIdle().get() + (moving ? stats().energyMoving().get() : 0.0)));
        }
        boolean empty = energy <= 0.0f;
        boolean recovered = energy > capacity() * 0.05f;
        if (empty && !emergency || emergency && recovered) {
            emergency = empty;
            if (emergency) {
                mover.stop();
                setTarget(null);
                playSound(com.projectrebirth.core.ModSounds.GARRISON_MACHINE_SHUTDOWN.get(), 1.0f, 0.8f);
            }
        }
        boolean noAmmo = node(RIGHT) != Node.NONE || node(LEFT) != Node.NONE
                ? !hasAmmo(RIGHT) && !hasAmmo(LEFT) : false;
        boolean now = core.active() && (emergency || noAmmo);
        if (now != lacking) {
            lacking = now;
            ConsoleLink.supply(this, core, lacking);
        }
        // 1.8.0: below retreatBelow it falls back until it is repaired to returnAbove
        tactics.retreating = com.projectrebirth.garrison.tactics.Tactics.retreating(core, tactics.retreating);
        boolean stuckNow = core.active() && mover.stuck();
        if (stuckNow != stuckReported) {
            stuckReported = stuckNow;
            ConsoleLink.report(this, core, ConsoleAlarm.STUCK, stuckNow);
        }
        entityData.set(DOWN, !working());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(stats().speed().get() * (core.emiFault() ? 0.6 : 1.0));
        if (!core.task().equals(lastTask)) {
            lastTask = core.task();
            cameFrom = null;
            holdPoint = blockPosition();
            routeIndex = 0;
        }
    }

    private boolean hasAmmo(int side) {
        return switch (node(side)) {
            case GUN -> rounds > 0;
            case ROCKET -> rockets > 0;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ the mind

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new com.projectrebirth.garrison.tactics.FleeGoal(this, this, () -> working() && !jetpack.flying()));
        goalSelector.addGoal(1, new ChargeGoal());
        goalSelector.addGoal(2, new FightGoal());
        goalSelector.addGoal(3, new TaskGoal());
        targetSelector.addGoal(1, new ChooseTargetGoal());
    }

    private boolean withinOrder(LivingEntity target) {
        MachineTask task = machineCore().task();
        if (task.type() == MachineTask.Type.GUARD && task.point().isPresent()) {
            double radius = stats().guardRadius().get();
            return target.blockPosition().distSqr(task.point().get()) <= radius * radius;
        }
        if (task.type() == MachineTask.Type.AREA_PATROL) {
            return AreaPatrol.covers(task, target.blockPosition());
        }
        return true;
    }

    private boolean validTarget(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target.level() != level()) {
            return false;
        }
        double range = stats().range().get();
        if (machineCore().calledTo(target, level().getGameTime())) {
            // 1.8.0: a machine of its group called it to this one: it comes from farther
            range += RebirthConfig.TACTICS_HELP_RADIUS.get();
        }
        return distanceToSqr(target) <= range * range * 1.5 && TargetRules.mayAttack(this, target) && withinOrder(target);
    }

    private final class ChooseTargetGoal extends Goal {
        private int wait;

        ChooseTargetGoal() {
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!working() || --wait > 0) {
                return false;
            }
            wait = 5;
            if (com.projectrebirth.garrison.tactics.Tactics.on()) {
                // 1.8.0: by priority (whoever hits its people, then it, then the one it was called to, then the dangerous ones)
                LivingEntity current = validTarget(getTarget()) ? getTarget() : null;
                LivingEntity best = com.projectrebirth.garrison.tactics.Tactics.choose(GuardMechEntity.this, GuardMechEntity.this, stats().range().get(), current, GuardMechEntity.this::validTarget);
                if (best == null) {
                    LivingEntity called = com.projectrebirth.garrison.tactics.Tactics.calledTarget(GuardMechEntity.this, machineCore());
                    best = called != null && validTarget(called) ? called : null;
                }
                if (best != getTarget()) {
                    setTarget(best);
                    if (best != null) {
                        targetSince = level().getGameTime();
                    }
                }
                return false;
            }
            if (validTarget(getTarget())) {
                return false;
            }
            double range = stats().range().get();
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (LivingEntity found : level().getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(range),
                    found -> found != GuardMechEntity.this && found.isAlive())) {
                double distance = distanceToSqr(found);
                if (distance < bestDistance && distance <= range * range && hasLineOfSight(found) && validTarget(found)) {
                    best = found;
                    bestDistance = distance;
                }
            }
            setTarget(best);
            if (best != null) {
                targetSince = level().getGameTime();
            }
            return false;
        }
    }

    /** Closes in to shooting distance (as far as its order lets it) and fires both nodes. */
    private final class FightGoal extends Goal {
        FightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return working() && validTarget(getTarget());
        }

        @Override
        public void start() {
            entityData.set(AIMING, true);
        }

        @Override
        public void stop() {
            entityData.set(AIMING, false);
            lastTarget = null;
            lockTicks = 0;
            mover.stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            if (com.projectrebirth.garrison.tactics.Tactics.on()) {
                fightWithTactics(target);
                return;
            }
            getLookControl().setLookAt(target, 30.0f, 30.0f);
            double distance = Math.sqrt(distanceToSqr(target));
            boolean sees = getSensing().hasLineOfSight(target);
            MachineTask task = machineCore().task();
            boolean holding = task.type() == MachineTask.Type.HOLD && holdPoint != null;
            double leash = RebirthConfig.ANDROID_HOLD_RADIUS.get();
            boolean mayChase = !holding || target.blockPosition().distSqr(holdPoint) <= (leash + 2) * (leash + 2);
            double range = stats().range().get();
            if (jetpack.flying()) {
                // in the air the jetpack carries it; the guns go on firing
            } else if ((!sees || distance > range * 0.6) && mayChase) {
                mover.walk(target.position(), 1.0);
            } else {
                mover.stop();
            }
            // 1.7.2: up close it stomps; out of its reach on foot (high up, or no way to it), or now and then, it takes off over the target
            // 1.7.3: an enemy within the stomp's reach is stomped, never flown away from on a whim
            boolean inReach = canStomp() && distance <= RebirthConfig.GUARD_MECH_STOMP_RADIUS.get() + getBbWidth() * 0.5;
            if (!jetpack.flying() && inReach && onGround() && stompWindup < 0 && stompCooldown <= 0) {
                stompWindup = STOMP_WINDUP;
                stompCooldown = RebirthConfig.GUARD_MECH_STOMP_COOLDOWN.get();
                triggerAnim("main", "stomp");
            } else if (mayChase && !jetpack.flying() && jetpack.ready()) {
                int chance = RebirthConfig.GUARD_MECH_COMBAT_FLIGHT_CHANCE.get();
                boolean outOfReach = target.getY() - getY() > 2.5 || mover.stuck();
                if (outOfReach || sees && !inReach && chance > 0 && random.nextInt(chance) == 0) {
                    jetpack.overFight(target);
                }
            }
            if (target != lastTarget) {
                lastTarget = target;
                lockTicks = 0;
                playSound(com.projectrebirth.core.ModSounds.GARRISON_TARGET_LOCK.get(), 1.0f, 1.0f);
            }
            if (!sees || distance > range) {
                return;
            }
            if (lockTicks < 12) {
                lockTicks++;
                return;
            }
            if (!aimedAt(target, 20.0)) {
                return;
            }
            for (int side = 0; side < 2; side++) {
                fireSide(side, target);
            }
        }
    }

    // ------------------------------------------------------------------ 1.8.0, block I: the tactics of a mech and a small robot

    /** Its tactics (tests). */
    public com.projectrebirth.garrison.tactics.TacticsState tactics() {
        return tactics;
    }

    /** The radius of the area its order keeps it in. */
    private double orderRadius() {
        return switch (machineCore().task().type()) {
            case AREA_PATROL -> RebirthConfig.AREA_PATROL_RADIUS.get() + 8.0;
            case HOLD -> RebirthConfig.ANDROID_HOLD_RADIUS.get() + 2.0;
            default -> stats().guardRadius().get();
        };
    }

    /** A point that far away from the enemy that it gets to on foot (no wall, pit or lava behind it), or null. */
    @Nullable
    private Vec3 awayFrom(LivingEntity enemy, double distance) {
        long now = level().getGameTime();
        Vec3 cached = tactics.cached(1, now);
        if (cached != null && cached.distanceToSqr(position()) > 1.0) {
            return cached;
        }
        return tactics.cache(1, now, awayFromNow(enemy, distance));
    }

    @Nullable
    private Vec3 awayFromNow(LivingEntity enemy, double distance) {
        Vec3 away = position().subtract(enemy.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0e-4) {
            return null;
        }
        away = away.normalize();
        for (double turn : new double[] {0.0, 35.0, -35.0, 70.0, -70.0}) {
            double rad = turn * Mth.DEG_TO_RAD;
            Vec3 dir = new Vec3(away.x * Math.cos(rad) - away.z * Math.sin(rad), 0, away.x * Math.sin(rad) + away.z * Math.cos(rad));
            Vec3 point = position().add(dir.scale(distance));
            if (mover.clearWay(point)) {
                return point;
            }
        }
        return null;
    }

    /** When its claw last struck (tests). */
    private long lastClaw = Long.MIN_VALUE;

    public long lastClaw() {
        return lastClaw;
    }

    /**
     * The fight with tactics. Both: the target by priority, a call to its group, falling back when badly hurt (firing), a step
     * aside when one of its own is on the line of fire, never past the area of its order. A small robot circles its target at
     * its distance (fanned out from the other robots), backs away from an enemy up close with a look behind it, takes cover when
     * hurt, and strikes with its claw only when it is pinned. A mech stands between a big enemy and its androids or work machines,
     * stomps and steps back when surrounded, fires rockets only at a group or a big target, never near its own.
     */
    private void fightWithTactics(LivingEntity target) {
        getLookControl().setLookAt(target, 30.0f, 30.0f);
        long now = level().getGameTime();
        double distance = Math.sqrt(distanceToSqr(target));
        boolean sees = getSensing().hasLineOfSight(target);
        double range = stats().range().get();
        boolean robot = !canStomp();
        if (now >= tactics.nextCall) {
            tactics.nextCall = now + 60;
            com.projectrebirth.garrison.tactics.Tactics.callForHelp(this, this, target);
        }
        Vec3 anchor = com.projectrebirth.garrison.tactics.Tactics.anchor(this, machineCore().task(), holdPoint);
        Vec3 move = null;
        boolean pinned = false;
        Vec3 aside = tactics.aside();
        if (jetpack.flying()) {
            // in the air the jetpack carries it; the guns go on firing
        } else if (tactics.retreating) {
            if (tactics.retreatTo == null || now - tactics.retreatAt > 40) {
                tactics.retreatTo = com.projectrebirth.garrison.tactics.Tactics.retreatPoint(this, this, target);
                tactics.retreatAt = now;
            }
            move = tactics.retreatTo;
        } else if (tactics.backOff > 0) {
            tactics.backOff--;
            move = tactics.backTo;
        } else if (aside != null) {
            move = aside;
        } else if (robot) {
            java.util.Optional<LivingEntity> close = com.projectrebirth.garrison.tactics.Tactics.meleeWithin(this, this, RebirthConfig.ROBOT_BACK_OFF.get());
            if (close.isPresent()) {
                move = awayFrom(close.get(), 3.0);
                pinned = move == null && close.get().distanceTo(this) < 1.6 + getBbWidth() * 0.5;
                if (move == null) {
                    move = circlePoint(target, range);
                }
            } else {
                move = robotMove(target, distance, sees, range);
            }
        } else {
            move = mechMove(target, distance, sees, range, now);
        }
        if (move != null && !tactics.retreating && com.projectrebirth.garrison.tactics.Tactics.leashed(anchor, orderRadius(), move)) {
            Vec3 back = move.subtract(anchor).multiply(1, 0, 1);
            move = back.lengthSqr() < 1.0e-4 ? null : anchor.add(back.normalize().scale(orderRadius())).add(0, move.y - anchor.y, 0);
        }
        if (move != null) {
            walkTo(move);
        } else if (!jetpack.flying()) {
            mover.stop();
        }
        if (robot && pinned && now - lastClaw >= RebirthConfig.ROBOT_CLAW_COOLDOWN.get()) {
            // pinned against something with an enemy on it: the claw
            LivingEntity close = com.projectrebirth.garrison.tactics.Tactics.meleeWithin(this, this, 2.0).orElse(null);
            if (close != null && close.hurt(damageSources().mobAttack(this), RebirthConfig.ROBOT_CLAW_DAMAGE.get().floatValue())) {
                lastClaw = now;
                swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                playSound(SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 1.4f);
            }
        }
        if (!robot) {
            mechCloseFight(target, distance, sees, now);
        }
        if (target != lastTarget) {
            lastTarget = target;
            lockTicks = 0;
            playSound(com.projectrebirth.core.ModSounds.GARRISON_TARGET_LOCK.get(), 1.0f, 1.0f);
        }
        if (!sees || distance > range) {
            // no shot without a line of sight or out of range: no rounds thrown away
            return;
        }
        if (lockTicks < 12) {
            lockTicks++;
            return;
        }
        if (!aimedAt(target, 20.0)) {
            return;
        }
        if (!lineClear(target)) {
            Vec3 step = com.projectrebirth.garrison.tactics.Tactics.sideStep(this, this, mover, target, getEyeHeight());
            if (step != null) {
                tactics.stepAside(step, 20);
            }
            return;
        }
        for (int side = 0; side < 2; side++) {
            if (node(side) == Node.ROCKET && !rocketWorthy(target)) {
                continue;
            }
            fireSide(side, target);
        }
    }

    private boolean lineClear(LivingEntity target) {
        long now = level().getGameTime();
        if (now != tactics.lineAt) {
            tactics.lineAt = now;
            tactics.lineClear = com.projectrebirth.garrison.tactics.Tactics.clearLine(this, this, muzzle(RIGHT), target);
        }
        return tactics.lineClear;
    }

    /** A rocket is for a big target or a group (mechRocketGroup enemies round it); never near its own (fireSide checks that). */
    public boolean rocketWorthy(LivingEntity target) {
        if (com.projectrebirth.garrison.tactics.Tactics.big(target)) {
            return true;
        }
        int group = level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0),
                e -> e.isAlive() && TargetRules.mayAttack(this, e)).size();
        return group >= RebirthConfig.MECH_ROCKET_GROUP.get();
    }

    /** A small robot: closes in until it sees the target in range, then circles it at its distance. */
    @Nullable
    private Vec3 robotMove(LivingEntity target, double distance, boolean sees, double range) {
        if (!sees || distance > range) {
            return target.position();
        }
        boolean hurt = machineCore().structural() < machineCore().maxStructural() * RebirthConfig.TACTICS_COVER_BELOW.get();
        if (hurt && !com.projectrebirth.garrison.tactics.Tactics.melee(target)) {
            long now = level().getGameTime();
            if (now - tactics.coverAt > 40) {
                tactics.coverAt = now;
                tactics.cover = com.projectrebirth.garrison.tactics.Tactics.cover(this, mover, target);
            }
            if (tactics.cover != null) {
                // between bursts behind the cover, out to the spot next to it to fire
                return cooldown[RIGHT] > 10 ? tactics.cover.hide() : tactics.cover.peek();
            }
        }
        return circlePoint(target, range);
    }

    /**
     * The next point on its circle round the target, a fifth of a turn on; it turns the other way now and then, when the way is
     * not free, and when another small robot of its own is near the point (they fan out).
     */
    @Nullable
    private Vec3 circlePoint(LivingEntity target, double range) {
        long now = level().getGameTime();
        Vec3 cached = tactics.cached(2, now);
        if (cached != null && cached.distanceToSqr(position()) > 1.0) {
            return cached;
        }
        return tactics.cache(2, now, circlePointNow(target, range, now));
    }

    @Nullable
    private Vec3 circlePointNow(LivingEntity target, double range, long now) {
        if (now >= tactics.strafeTurnAt) {
            tactics.strafeTurnAt = now + 100 + random.nextInt(100);
            tactics.strafeSign = random.nextBoolean() ? 1 : -1;
        }
        double radius = Math.max(3.0, range * RebirthConfig.ROBOT_CIRCLE_SHARE.get());
        double angle = com.projectrebirth.garrison.tactics.Tactics.angleFrom(target, position());
        double spacing = RebirthConfig.ROBOT_FAN_SPACING.get();
        for (int attempt = 0; attempt < 4; attempt++) {
            int sign = attempt % 2 == 0 ? tactics.strafeSign : -tactics.strafeSign;
            double step = (attempt < 2 ? 0.35 : 0.7) * sign;
            Vec3 point = com.projectrebirth.garrison.tactics.Tactics.onCircle(target, radius, angle + step);
            boolean crowded = spacing > 0.0 && !level().getEntitiesOfClass(GuardMechEntity.class, new AABB(point, point).inflate(spacing),
                    e -> e != this && e.isAlive() && !e.canStomp() && TargetRules.friendly(this, e)).isEmpty();
            if (!crowded && mover.clearWay(point)) {
                if (attempt % 2 == 1) {
                    tactics.strafeSign = -tactics.strafeSign;
                }
                return point;
            }
        }
        return null;
    }

    /** A mech: between a big enemy and its own; else in range, at six tenths of it. */
    @Nullable
    private Vec3 mechMove(LivingEntity target, double distance, boolean sees, double range, long now) {
        if (com.projectrebirth.garrison.tactics.Tactics.big(target) || target.getType().is(com.projectrebirth.garrison.tactics.Tactics.DANGEROUS)) {
            double radius = RebirthConfig.MECH_SHIELD_RADIUS.get();
            for (LivingEntity friend : level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
                    e -> e != this && e.isAlive() && (e instanceof AndroidEntity || e instanceof GarrisonMachine m && m.consoleWorker())
                            && TargetRules.friendly(this, e))) {
                Vec3 between = target.position().subtract(friend.position()).multiply(1, 0, 1);
                if (between.lengthSqr() > 16.0 && distanceToSqr(friend) < 32.0 * 32.0) {
                    // a few blocks out from the friend toward the threat: it takes the blows
                    return friend.position().add(between.normalize().scale(3.0 + getBbWidth()));
                }
            }
        }
        if (!sees || distance > range * 0.6) {
            return target.position();
        }
        // rockets only: out of its own blast (friendRadius round the target and its half width), else they never go
        boolean gun = node(RIGHT) == Node.GUN && hasAmmo(RIGHT) || node(LEFT) == Node.GUN && hasAmmo(LEFT);
        boolean inBlast = getBoundingBox().intersects(target.getBoundingBox().inflate(RebirthConfig.ROCKET_TURRET_FRIEND_RADIUS.get() + 1.0));
        if (!gun && (hasAmmo(RIGHT) || hasAmmo(LEFT)) && (distance < 7.0 || inBlast)) {
            return awayFrom(target, 3.0);
        }
        return null;
    }

    /** A mech up close: surrounded, it stomps and steps back; out of its reach on foot, it flies. */
    private void mechCloseFight(LivingEntity target, double distance, boolean sees, long now) {
        boolean inReach = distance <= RebirthConfig.GUARD_MECH_STOMP_RADIUS.get() + getBbWidth() * 0.5;
        int around = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.0),
                e -> e != this && e.isAlive() && TargetRules.mayAttack(this, e)).size();
        boolean surrounded = around >= RebirthConfig.MECH_SURROUNDED.get();
        if (!jetpack.flying() && (inReach || surrounded) && onGround() && stompWindup < 0 && stompCooldown <= 0) {
            stompWindup = STOMP_WINDUP;
            stompCooldown = RebirthConfig.GUARD_MECH_STOMP_COOLDOWN.get();
            triggerAnim("main", "stomp");
            // then a step back, to fire
            Vec3 back = awayFrom(target, 3.0);
            if (back != null) {
                tactics.backOff = 20;
                tactics.backTo = back;
            }
            return;
        }
        if (!jetpack.flying() && jetpack.ready()) {
            boolean outOfReach = target.getY() - getY() > 2.5 || mover.stuck();
            if (outOfReach && com.projectrebirth.garrison.tactics.Tactics.big(target)) {
                // behind a big target it cannot walk up to
                Vec3 behind = target.position().subtract(position()).multiply(1, 0, 1);
                if (behind.lengthSqr() > 1.0e-4 && jetpack.hop(target.position().add(behind.normalize().scale(target.getBbWidth() + 3.0)))) {
                    return;
                }
            }
            int chance = RebirthConfig.GUARD_MECH_COMBAT_FLIGHT_CHANCE.get();
            if (outOfReach || sees && !inReach && chance > 0 && random.nextInt(chance) == 0) {
                jetpack.overFight(target);
            }
        }
    }

    private boolean aimedAt(LivingEntity target, double degrees) {
        Vec3 to = target.getEyePosition().subtract(getEyePosition());
        double wanted = Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG - 90.0;
        return Math.abs(Mth.wrapDegrees(wanted - getYHeadRot())) <= degrees;
    }

    /** Where the guns of a side are, in the world: to the side of the torso and a little ahead. */
    public Vec3 muzzle(int side) {
        double yaw = getYHeadRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        boolean rocket = node(side) == Node.ROCKET;
        return position().add(0.0, rocket ? 3.0 : 2.2, 0.0).add(right.scale(side == RIGHT ? 0.9 : -0.9)).add(forward.scale(rocket ? 0.3 : 1.8));
    }

    /** One shot of a side if it is ready: a bullet of a burst, or a rocket. Public for tests. */
    public boolean fireSide(int side, LivingEntity target) {
        if (cooldown[side] > 0 || energy < stats().energyShot().get()) {
            return false;
        }
        switch (node(side)) {
            case GUN -> {
                if (rounds <= 0) {
                    return false;
                }
                if (shotsLeft[side] <= 0) {
                    shotsLeft[side] = stats().burst().get();
                }
                rounds--;
                energy -= stats().energyShot().get();
                double spread = stats().spread().get() * (machineCore().dusty() ? RebirthConfig.TURRET_DUST_SPREAD.get() : 1.0)
                        + (machineCore().emiFault() ? RebirthConfig.TURRET_EMI_SPREAD.get() * 0.5 : 0.0);
                Gunnery.fire(this, muzzle(side), target, spread, stats().damage().get().floatValue(), stats().range().get() + 4.0, 0.8f,
                        e -> e != this && !TargetRules.friendly(this, e), SoundSource.NEUTRAL);
                triggerAnim("main", side == RIGHT ? "shot_right" : "shot_left");
                shotsLeft[side]--;
                cooldown[side] = shotsLeft[side] > 0 ? stats().shotTicks().get() : stats().pauseTicks().get();
                return true;
            }
            case ROCKET -> {
                if (rockets <= 0 || distanceToSqr(target) < 36.0 || friendNear(target)) {
                    cooldown[side] = 10;
                    return false;
                }
                rockets--;
                energy -= stats().energyShot().get();
                Vec3 from = muzzle(side);
                Vec3 to = target.position();
                if (com.projectrebirth.garrison.tactics.Tactics.on()) {
                    // 1.8.0: where a moving target will be when the rocket comes down
                    to = com.projectrebirth.garrison.tactics.Tactics.leadGround(target, 14 + (int) (from.distanceTo(to) * 0.6));
                }
                level().addFreshEntity(new MechRocketEntity(level(), this, from, to, 14 + (int) (from.distanceTo(to) * 0.6),
                        RebirthConfig.ROCKET_TURRET_POWER.get().floatValue(), RebirthConfig.ROCKET_TURRET_BREAKS.get()));
                playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.7f);
                triggerAnim("main", side == RIGHT ? "rocket_right" : "rocket_left");
                cooldown[side] = stats().rocketTicks().get();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Whether one of its own is within a rocket's blast of the target. Public for tests. */
    public boolean friendNear(LivingEntity target) {
        double radius = RebirthConfig.ROCKET_TURRET_FRIEND_RADIUS.get();
        return !level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
                found -> found != target && found.isAlive() && (found == this || TargetRules.friendly(this, found))).isEmpty();
    }

    /** 1.7.2: how it gets to places. Public for tests. */
    public MachineMover mover() {
        return mover;
    }

    /** 1.7.2: towards a place, by the mover (a path, or straight when the way is free; it gets itself unstuck). */
    private void walkTo(Vec3 goal) {
        if (!jetpack.flying()) {
            mover.walk(goal, 1.0);
        }
    }

    /** 1.7.2: whether a weapon it carries is low on ammunition (at most the config's share of its magazine left). */
    public boolean needsAmmo() {
        double share = RebirthConfig.POST_RELOAD_SHARE.get();
        for (int side = 0; side < 2; side++) {
            switch (node(side)) {
                case GUN -> {
                    if (rounds <= stats().rounds().get() * share) {
                        return true;
                    }
                }
                case ROCKET -> {
                    if (rockets <= stats().rockets().get() * share) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    /** 1.7.2: whether its magazines are full for the weapons it carries. */
    private boolean ammoFull() {
        for (int side = 0; side < 2; side++) {
            if (node(side) == Node.GUN && rounds < stats().rounds().get() || node(side) == Node.ROCKET && rockets < stats().rockets().get()) {
                return false;
            }
        }
        return true;
    }

    private boolean lowEnergy() {
        return energy <= capacity() * stats().lowEnergy().get();
    }

    /**
     * Low on energy, or (1.7.2) on ammunition: to the nearest service post (for energy alone the small robot takes a charging pad
     * too), and stays on it until charged and loaded. With no post in reach it reports that to its console; for energy it goes to
     * the console. A post that gives no ammunition for a while (an empty chest) is left, and it does not come back for that soon.
     */
    private final class ChargeGoal extends Goal {
        @Nullable
        private Vec3 post;
        private int wait;
        private boolean forAmmo;
        private int ammoWait;
        private int lastAmmo;

        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!machineCore().active() || isRollingOut() || --wait > 0 || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
                return false;
            }
            wait = 10;
            boolean low = lowEnergy();
            forAmmo = needsAmmo() && level().getGameTime() >= ammoRetry
                    // 1.8.0: not in the middle of a fight while it has rounds left: it loads in a pause
                    && !(com.projectrebirth.garrison.tactics.Tactics.on() && getTarget() != null && (hasAmmo(RIGHT) || hasAmmo(LEFT)));
            if (!low && !forAmmo) {
                reportNoPost(false);
                return false;
            }
            post = place(server, low);
            reportNoPost(post == null);
            if (post == null && low) {
                // no post in reach: to its console, where its owner finds it
                post = postOrConsole(GuardMechEntity.this, machineCore());
            }
            ammoWait = 0;
            lastAmmo = rounds + rockets;
            return post != null;
        }

        @Override
        public void start() {
            if (post != null && distanceToSqr(post) > 9.0) {
                cameFrom = position();
            }
        }

        /** The post to go to (low on energy: the nearest place to charge first, for the small robot a pad too), or null. */
        @Nullable
        private Vec3 place(net.minecraft.server.level.ServerLevel server, boolean low) {
            return low ? chargePoint(server) : ServicePostBlockEntity.nearest(server, blockPosition());
        }

        @Override
        public boolean canContinueToUse() {
            if (post == null || !machineCore().active()) {
                return false;
            }
            if (energy < capacity() * stats().charged().get()) {
                return true;
            }
            return forAmmo && !ammoFull() && ammoWait < RebirthConfig.POST_AMMO_WAIT_SECONDS.get() * 20;
        }

        @Override
        public void stop() {
            mover.stop();
            if (forAmmo && !ammoFull()) {
                // the post had nothing to give: not again for five minutes (its console has the alarm of no ammunition)
                ammoRetry = level().getGameTime() + 6000;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            // a nearer post may have come up (or been built) since it set out
            if (tickCount % 40 == 0 && level() instanceof net.minecraft.server.level.ServerLevel server) {
                Vec3 nearer = place(server, lowEnergy());
                if (nearer != null) {
                    post = nearer;
                }
            }
            if (post != null && distanceToSqr(post) > 0.6) {
                walkTo(post);
                ammoWait = 0;
            } else {
                mover.stop();
                int ammo = rounds + rockets;
                ammoWait = ammo > lastAmmo ? 0 : ammoWait + 1;
                lastAmmo = ammo;
            }
        }
    }

    private void reportNoPost(boolean on) {
        if (on != noPostReported) {
            noPostReported = on;
            ConsoleLink.report(this, machineCore(), ConsoleAlarm.NO_POST, on);
        }
    }

    /** Its order when there is nothing to fight. */
    private final class TaskGoal extends Goal {
        TaskGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return working() && getTarget() == null;
        }

        @Override
        public void stop() {
            mover.stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Vec3 goal = goal();
            if (goal == null) {
                mover.stop();
                return;
            }
            // 1.7.1: a mech is wide and its path ends a block or two short of the point: within three blocks it is there
            double near = machineCore().task().type() == MachineTask.Type.ESCORT ? 25.0 : 9.0;
            if (distanceToSqr(goal) <= near) {
                mover.stop();
                if (goal == cameFrom) {
                    cameFrom = null;
                }
                if (machineCore().task().type() == MachineTask.Type.AREA_PATROL) {
                    areaPatrol.arrived();
                }
                MachineTask task = machineCore().task();
                if (task.type() == MachineTask.Type.PATROL && !task.route().isEmpty()) {
                    routeIndex = (routeIndex + 1) % task.route().size();
                }
                return;
            }
            walkTo(goal);
        }

        @Nullable
        private Vec3 goal() {
            MachineTask task = machineCore().task();
            if (tactics.retreating && level() instanceof net.minecraft.server.level.ServerLevel server) {
                // 1.8.0: to a service post to be repaired; with none, it follows its order
                Vec3 post = ServicePostBlockEntity.nearest(server, blockPosition());
                if (post != null) {
                    return post;
                }
            }
            return switch (task.type()) {
                case GUARD -> task.point().map(Vec3::atBottomCenterOf).orElse(null);
                case HOLD -> holdPoint == null ? null : Vec3.atBottomCenterOf(holdPoint);
                case PATROL -> task.route().isEmpty() ? null
                        : Vec3.atBottomCenterOf(task.route().get(Math.min(routeIndex, task.route().size() - 1)).above());
                case ESCORT -> task.escort().map(id -> level().getPlayerByUUID(id)).filter(p -> p.distanceToSqr(GuardMechEntity.this) < 64 * 64)
                        .map(Player::position).orElse(null);
                case TO_POST, SHELTER -> postOrConsole(GuardMechEntity.this, machineCore());
                case AREA_PATROL -> areaPatrol.goal(task);
                // 1.7.2: no order to follow: back to where it was before it went to the post
                default -> cameFrom;
            };
        }
    }

    /** Where a machine goes "to the post": the nearest service post, else its console. */
    @Nullable
    static Vec3 postOrConsole(net.minecraft.world.entity.Entity self, MachineCore core) {
        if (self.level() instanceof net.minecraft.server.level.ServerLevel server) {
            Vec3 post = ServicePostBlockEntity.nearest(server, self.blockPosition());
            if (post != null) {
                return post;
            }
        }
        GlobalPos console = core.console();
        return console != null && console.dimension().equals(self.level().dimension()) ? Vec3.atBottomCenterOf(console.pos().above()) : null;
    }

    // ------------------------------------------------------------------ the route

    /**
     * The first place on a patrol route too narrow for a mech (three blocks wide, four high, on the way between two points), or
     * null. The route is given all the same; the player is warned.
     */
    @Nullable
    public static BlockPos narrowPlace(Level level, List<BlockPos> route) {
        for (int i = 0; i < route.size(); i++) {
            BlockPos from = route.get(i).above();
            BlockPos to = route.get((i + 1) % route.size()).above();
            int steps = (int) Math.ceil(Math.sqrt(from.distSqr(to)));
            for (int s = 0; s <= steps; s++) {
                double t = steps == 0 ? 0 : (double) s / steps;
                BlockPos at = BlockPos.containing(Mth.lerp(t, from.getX(), to.getX()) + 0.5, Mth.lerp(t, from.getY(), to.getY()),
                        Mth.lerp(t, from.getZ(), to.getZ()) + 0.5);
                if (!level.isLoaded(at)) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = 0; dy <= 3; dy++) {
                            BlockPos check = at.offset(dx, dy, dz);
                            if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty()) {
                                return at;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RightNode", node(RIGHT).ordinal());
        tag.putInt("LeftNode", node(LEFT).ordinal());
        tag.putFloat("Energy", energy);
        tag.putInt("Rounds", rounds);
        tag.putInt("Rockets", rockets);
        tag.putBoolean("Emergency", emergency);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("RightNode")) {
            setNode(RIGHT, Node.byId(tag.getInt("RightNode")));
            setNode(LEFT, Node.byId(tag.getInt("LeftNode")));
        }
        // a mech summoned without its energy (a command) starts charged, as one from the dock
        energy = tag.contains("Energy") ? tag.getFloat("Energy") : capacity();
        rounds = tag.getInt("Rounds");
        rockets = tag.getInt("Rockets");
        emergency = tag.getBoolean("Emergency");
        // 1.7.2: a flight is not saved; it stands on the ground again
        jetpack.reset();
    }

    // ------------------------------------------------------------------ the look

    @Override
    public int getMaxHeadYRot() {
        return 75;
    }

    /** 1.7.1: the start of its animations' names in its animation file. */
    protected String animationPrefix() {
        return "animation.guard_mech";
    }

    /** 1.7.1: whether a weapon node in hand swaps one of its nodes (the small robot has its gun built in). */
    protected boolean swapsNodes() {
        return true;
    }

    /** 1.7.1: whether a charging pad charges it (the small robot); the mech charges only at a service post. */
    public boolean chargesOnPads() {
        return false;
    }

    /** Where it goes to charge: the nearest service post (the small robot: or charging pad, whichever is nearer), or null. */
    @Nullable
    protected Vec3 chargePoint(net.minecraft.server.level.ServerLevel server) {
        return ServicePostBlockEntity.nearest(server, blockPosition());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = RawAnimation.begin().thenLoop(animationPrefix() + ".idle");
        RawAnimation walk = RawAnimation.begin().thenLoop(animationPrefix() + ".walk");
        // 1.7.1, block I: with no structural integrity it sinks down and stays so; a wreck slumps further and lies there
        RawAnimation shutdown = RawAnimation.begin().thenPlayAndHold(animationPrefix() + ".shutdown");
        RawAnimation wrecked = RawAnimation.begin().thenPlayAndHold(animationPrefix() + ".wrecked");
        RawAnimation fly = RawAnimation.begin().thenLoop(animationPrefix() + ".fly");
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            MachineCore.State shown = shownState();
            if (shown == MachineCore.State.WRECK) {
                return state.setAndContinue(wrecked);
            }
            if (shown == MachineCore.State.DISABLED) {
                return state.setAndContinue(shutdown);
            }
            if (down()) {
                return state.setAndContinue(idle);
            }
            if (flying()) {
                return state.setAndContinue(fly);
            }
            double dx = getX() - xo, dz = getZ() - zo;
            return state.setAndContinue(state.isMoving() || dx * dx + dz * dz > 1.0e-5 ? walk : idle);
        }).triggerableAnim("shot_right", RawAnimation.begin().thenPlay(animationPrefix() + ".shot_right"))
                .triggerableAnim("shot_left", RawAnimation.begin().thenPlay(animationPrefix() + ".shot_left"))
                .triggerableAnim("rocket_right", RawAnimation.begin().thenPlay(animationPrefix() + ".rocket_right"))
                .triggerableAnim("rocket_left", RawAnimation.begin().thenPlay(animationPrefix() + ".rocket_left"))
                .triggerableAnim("stomp", RawAnimation.begin().thenPlay(animationPrefix() + ".stomp")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.8f, 0.8f);
    }
}
