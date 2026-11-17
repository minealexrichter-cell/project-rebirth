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
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.guard_mech.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.guard_mech.walk");

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

    /** What a packed mech keeps: its nodes, energy and magazine. */
    public record Loadout(int right, int left, int energy, int rounds, int rockets) {
        public static final Loadout DEFAULT = new Loadout(Node.GUN.ordinal(), Node.ROCKET.ordinal(), 0, 0, 0);
        public static final Codec<Loadout> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("right", 1).forGetter(Loadout::right),
                Codec.INT.optionalFieldOf("left", 2).forGetter(Loadout::left),
                Codec.INT.optionalFieldOf("energy", 0).forGetter(Loadout::energy),
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

    public GuardMechEntity(EntityType<? extends GuardMechEntity> type, Level level) {
        super(type, level, MachineKind.MECH);
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
        return RebirthConfig.GUARD_MECH_ENERGY_CAPACITY.get();
    }

    public int rounds() {
        return rounds;
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
            int room = stack.is(WeaponRegistry.ASSAULT_RIFLE_AMMO.get()) ? RebirthConfig.GUARD_MECH_ROUNDS.get() - rounds
                    : stack.is(GarrisonRegistry.ROCKET.get()) ? RebirthConfig.GUARD_MECH_ROCKETS.get() - rockets : 0;
            if (room > 0) {
                ItemStack taken = store.extractItem(slot, room, false);
                load(taken);
            }
        }
    }

    /** Puts rounds or rockets into its magazine as far as there is room; what does not fit stays in the stack. */
    public void load(ItemStack stack) {
        if (stack.is(WeaponRegistry.ASSAULT_RIFLE_AMMO.get())) {
            int put = Math.min(stack.getCount(), RebirthConfig.GUARD_MECH_ROUNDS.get() - rounds);
            rounds += put;
            stack.shrink(put);
        } else if (stack.is(GarrisonRegistry.ROCKET.get())) {
            int put = Math.min(stack.getCount(), RebirthConfig.GUARD_MECH_ROCKETS.get() - rockets);
            rockets += put;
            stack.shrink(put);
        }
    }

    public boolean working() {
        return machineCore().active() && !emergency && RebirthConfig.MECHS_ENABLED.get() && RebirthConfig.GARRISON_ENABLED.get();
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
        energy = Mth.clamp(loadout.energy(), 0, capacity());
        rounds = loadout.rounds();
        rockets = loadout.rockets();
    }

    // ------------------------------------------------------------------ hands

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Node node = Node.of(held);
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
        if (tickCount % 20 == 0) {
            second();
        }
    }

    private void second() {
        MachineCore core = machineCore();
        if (core.active()) {
            boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0e-4;
            energy = Math.max(0.0f, energy - (float) (RebirthConfig.GUARD_MECH_ENERGY_IDLE.get() + (moving ? RebirthConfig.GUARD_MECH_ENERGY_MOVING.get() : 0.0)));
        }
        boolean empty = energy <= 0.0f;
        boolean recovered = energy > capacity() * 0.05f;
        if (empty && !emergency || emergency && recovered) {
            emergency = empty;
            if (emergency) {
                getNavigation().stop();
                setTarget(null);
            }
        }
        boolean noAmmo = node(RIGHT) != Node.NONE || node(LEFT) != Node.NONE
                ? !hasAmmo(RIGHT) && !hasAmmo(LEFT) : false;
        boolean now = core.active() && (emergency || noAmmo);
        if (now != lacking) {
            lacking = now;
            ConsoleLink.supply(this, core, lacking);
        }
        entityData.set(DOWN, !working());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RebirthConfig.GUARD_MECH_SPEED.get() * (core.emiFault() ? 0.6 : 1.0));
        if (!core.task().equals(lastTask)) {
            lastTask = core.task();
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
        goalSelector.addGoal(1, new ChargeGoal());
        goalSelector.addGoal(2, new FightGoal());
        goalSelector.addGoal(3, new TaskGoal());
        targetSelector.addGoal(1, new ChooseTargetGoal());
    }

    private boolean withinOrder(LivingEntity target) {
        MachineTask task = machineCore().task();
        if (task.type() == MachineTask.Type.GUARD && task.point().isPresent()) {
            double radius = RebirthConfig.GUARD_MECH_GUARD_RADIUS.get();
            return target.blockPosition().distSqr(task.point().get()) <= radius * radius;
        }
        return true;
    }

    private boolean validTarget(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target.level() != level()) {
            return false;
        }
        double range = RebirthConfig.GUARD_MECH_RANGE.get();
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
            if (validTarget(getTarget())) {
                return false;
            }
            double range = RebirthConfig.GUARD_MECH_RANGE.get();
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
            getNavigation().stop();
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
            getLookControl().setLookAt(target, 30.0f, 30.0f);
            double distance = Math.sqrt(distanceToSqr(target));
            boolean sees = getSensing().hasLineOfSight(target);
            MachineTask task = machineCore().task();
            boolean holding = task.type() == MachineTask.Type.HOLD && holdPoint != null;
            double leash = RebirthConfig.ANDROID_HOLD_RADIUS.get();
            boolean mayChase = !holding || target.blockPosition().distSqr(holdPoint) <= (leash + 2) * (leash + 2);
            double range = RebirthConfig.GUARD_MECH_RANGE.get();
            if ((!sees || distance > range * 0.6) && mayChase) {
                if (tickCount % 10 == 0) {
                    getNavigation().moveTo(target, 1.0);
                }
            } else {
                getNavigation().stop();
            }
            if (target != lastTarget) {
                lastTarget = target;
                lockTicks = 0;
                playSound(SoundEvents.NOTE_BLOCK_BIT.value(), 1.2f, 1.3f);
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
        if (cooldown[side] > 0 || energy < RebirthConfig.GUARD_MECH_ENERGY_SHOT.get()) {
            return false;
        }
        switch (node(side)) {
            case GUN -> {
                if (rounds <= 0) {
                    return false;
                }
                if (shotsLeft[side] <= 0) {
                    shotsLeft[side] = RebirthConfig.GUARD_MECH_BURST.get();
                }
                rounds--;
                energy -= RebirthConfig.GUARD_MECH_ENERGY_SHOT.get();
                double spread = RebirthConfig.GUARD_MECH_SPREAD.get() * (machineCore().dusty() ? RebirthConfig.TURRET_DUST_SPREAD.get() : 1.0)
                        + (machineCore().emiFault() ? RebirthConfig.TURRET_EMI_SPREAD.get() * 0.5 : 0.0);
                Gunnery.fire(this, muzzle(side), target, spread, RebirthConfig.GUARD_MECH_DAMAGE.get().floatValue(), RebirthConfig.GUARD_MECH_RANGE.get() + 4.0, 0.8f,
                        e -> e != this && !TargetRules.friendly(this, e), SoundSource.NEUTRAL);
                triggerAnim("main", side == RIGHT ? "shot_right" : "shot_left");
                shotsLeft[side]--;
                cooldown[side] = shotsLeft[side] > 0 ? RebirthConfig.GUARD_MECH_SHOT_TICKS.get() : RebirthConfig.GUARD_MECH_PAUSE_TICKS.get();
                return true;
            }
            case ROCKET -> {
                if (rockets <= 0 || distanceToSqr(target) < 36.0 || friendNear(target)) {
                    cooldown[side] = 10;
                    return false;
                }
                rockets--;
                energy -= RebirthConfig.GUARD_MECH_ENERGY_SHOT.get();
                Vec3 from = muzzle(side);
                Vec3 to = target.position();
                level().addFreshEntity(new MechRocketEntity(level(), this, from, to, 14 + (int) (from.distanceTo(to) * 0.6),
                        RebirthConfig.ROCKET_TURRET_POWER.get().floatValue(), RebirthConfig.ROCKET_TURRET_BREAKS.get()));
                playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.7f);
                triggerAnim("main", side == RIGHT ? "rocket_right" : "rocket_left");
                cooldown[side] = RebirthConfig.GUARD_MECH_ROCKET_TICKS.get();
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

    /** Low on energy: to the nearest service post, and stays on it until charged. */
    private final class ChargeGoal extends Goal {
        @Nullable
        private Vec3 post;
        private int wait;

        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!machineCore().active() || energy > capacity() * RebirthConfig.GUARD_MECH_LOW_ENERGY.get() || --wait > 0
                    || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
                return false;
            }
            wait = 10;
            post = ServicePostBlockEntity.nearest(server, blockPosition());
            return post != null;
        }

        @Override
        public boolean canContinueToUse() {
            return post != null && machineCore().active() && energy < capacity() * RebirthConfig.GUARD_MECH_CHARGED.get();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (post != null && distanceToSqr(post) > 0.6) {
                if (tickCount % 10 == 0) {
                    getNavigation().moveTo(post.x, post.y, post.z, 1.0);
                }
            } else {
                getNavigation().stop();
            }
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
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Vec3 goal = goal();
            if (goal == null) {
                getNavigation().stop();
                return;
            }
            double near = machineCore().task().type() == MachineTask.Type.ESCORT ? 25.0 : 2.25;
            if (distanceToSqr(goal) <= near) {
                getNavigation().stop();
                MachineTask task = machineCore().task();
                if (task.type() == MachineTask.Type.PATROL && !task.route().isEmpty()) {
                    routeIndex = (routeIndex + 1) % task.route().size();
                }
                return;
            }
            if (tickCount % 10 == 0) {
                getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
            }
        }

        @Nullable
        private Vec3 goal() {
            MachineTask task = machineCore().task();
            return switch (task.type()) {
                case GUARD -> task.point().map(Vec3::atBottomCenterOf).orElse(null);
                case HOLD -> holdPoint == null ? null : Vec3.atBottomCenterOf(holdPoint);
                case PATROL -> task.route().isEmpty() ? null
                        : Vec3.atBottomCenterOf(task.route().get(Math.min(routeIndex, task.route().size() - 1)).above());
                case ESCORT -> task.escort().map(id -> level().getPlayerByUUID(id)).filter(p -> p.distanceToSqr(GuardMechEntity.this) < 64 * 64)
                        .map(Player::position).orElse(null);
                case TO_POST, SHELTER -> postOrConsole(GuardMechEntity.this, machineCore());
                default -> null;
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
        energy = tag.getFloat("Energy");
        rounds = tag.getInt("Rounds");
        rockets = tag.getInt("Rockets");
        emergency = tag.getBoolean("Emergency");
    }

    // ------------------------------------------------------------------ the look

    @Override
    public int getMaxHeadYRot() {
        return 75;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (down()) {
                return state.setAndContinue(IDLE);
            }
            double dx = getX() - xo, dz = getZ() - zo;
            return state.setAndContinue(state.isMoving() || dx * dx + dz * dz > 1.0e-5 ? WALK : IDLE);
        }).triggerableAnim("shot_right", RawAnimation.begin().thenPlay("animation.guard_mech.shot_right"))
                .triggerableAnim("shot_left", RawAnimation.begin().thenPlay("animation.guard_mech.shot_left"))
                .triggerableAnim("rocket_right", RawAnimation.begin().thenPlay("animation.guard_mech.rocket_right"))
                .triggerableAnim("rocket_left", RawAnimation.begin().thenPlay("animation.guard_mech.rocket_left")));
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
