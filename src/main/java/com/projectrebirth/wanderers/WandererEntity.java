package com.projectrebirth.wanderers;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.config.WastelandConfig;
import com.projectrebirth.environment.Environment;
import com.projectrebirth.environment.EnvironmentState;
import com.projectrebirth.hostile.Gunnery;
import com.projectrebirth.hostile.MutantEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 1.9.0, block E: a wanderer, one of a neutral group of 2–4 that walks the world: a human in power armor, an android, a
 * human in protective gear or a wandering robot (the kind by the entity type). The group goes from place to place behind its
 * head, makes camp at evening (a campfire, the others about it), waits a catastrophe out under a roof, and fights mutants and
 * other hostile creatures. It never attacks a player first; a player who hits one of them has the whole group against him for
 * a while ([wasteland.wanderers] hostileMinutes), other groups do not care. Right-click opens the group's trade. It breaks no
 * blocks and opens no doors. A group with no player near goes away. A fallen armor leaves its wreck, which the wrench takes
 * apart; an android and a robot leave wrecks like bodies.
 */
public class WandererEntity extends PathfinderMob implements GeoEntity, RangedAttackMob {
    public enum Kind {
        ARMORED("armored"), ANDROID("android"), SURVIVOR("survivor"), ROBOT("robot");

        public final String id;

        Kind(String id) {
            this.id = id;
        }

        public static Kind byId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return SURVIVOR;
        }
    }

    private static final EntityDataAccessor<String> VARIANT = SynchedEntityData.defineId(WandererEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> LEADER = SynchedEntityData.defineId(WandererEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double GROUP_RANGE = 64.0;
    private static final long DAY = 24000L;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID groupId;
    /** Players the group is hostile to, until which game time. */
    private final Map<UUID, Long> hostile = new HashMap<>();
    /** The head's offers (empty on the others). */
    private final List<WandererData.Trade> offers = new ArrayList<>();
    @Nullable
    private BlockPos destination;
    @Nullable
    private BlockPos campfire;
    private int gunCooldown;
    private int alone;

    public WandererEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes(double health, double armor) {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.ARMOR, armor).add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /** The kind, by the entity type (also while the constructor of the parent runs). */
    public Kind kind() {
        String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();
        return Kind.byId(path.substring(path.indexOf('_') + 1));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, "");
        builder.define(LEADER, false);
    }

    /** Armored: the armor variant; android: the body class; survivor: the skin; robot: nothing. */
    public String variant() {
        return entityData.get(VARIANT);
    }

    /** The body class of an android; a variant that is unknown or not set (a summon with its own data) is the basic robot. */
    public com.projectrebirth.bodies.BodyClass bodyClass() {
        com.projectrebirth.bodies.BodyClass body = com.projectrebirth.bodies.BodyClass.byId(variant());
        return body == null ? com.projectrebirth.bodies.BodyClass.BASIC_ROBOT : body;
    }

    /** A wanderer from an egg or a command: the look and gear of a random member of its kind (a group gives its own). */
    @Override
    @Nullable
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType type,
            @Nullable net.minecraft.world.entity.SpawnGroupData data) {
        data = super.finalizeSpawn(level, difficulty, type, data);
        if (type != net.minecraft.world.entity.MobSpawnType.EVENT) {
            WandererGroups.equipAlone(level.getLevel(), this);
        }
        return data;
    }

    public void setVariant(String variant) {
        entityData.set(VARIANT, variant);
    }

    public boolean isLeader() {
        return entityData.get(LEADER);
    }

    public void setLeader(boolean leader) {
        entityData.set(LEADER, leader);
    }

    @Nullable
    public UUID groupId() {
        return groupId;
    }

    public void setGroupId(UUID id) {
        this.groupId = id;
    }

    public List<WandererData.Trade> offers() {
        return offers;
    }

    public boolean hasGun() {
        return kind() == Kind.ARMORED || kind() == Kind.ROBOT;
    }

    public boolean hasBow() {
        ItemStack main = getMainHandItem();
        return main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem;
    }

    // ------------------------------------------------------------------ the group

    /** The other members of its group near it (itself too). */
    public List<WandererEntity> group() {
        if (groupId == null) {
            return List.of(this);
        }
        return level().getEntitiesOfClass(WandererEntity.class, getBoundingBox().inflate(GROUP_RANGE), w -> groupId.equals(w.groupId) && w.isAlive());
    }

    /** The head of its group, itself if there is none any more (it then takes over). */
    public WandererEntity leader() {
        if (isLeader()) {
            return this;
        }
        for (WandererEntity member : group()) {
            if (member.isLeader()) {
                return member;
            }
        }
        setLeader(true);
        if (level() instanceof ServerLevel server) {
            WandererGroups.rollOffers(server, this);
        }
        return this;
    }

    /** Whether the group is hostile to this player now. */
    public boolean hostileTo(Player player) {
        Long until = hostile.get(player.getUUID());
        return until != null && until > level().getGameTime();
    }

    /** The whole group turns on a player who hurt one of them, for a while. */
    public void turnOn(Player player) {
        long until = level().getGameTime() + WastelandConfig.WANDERERS_HOSTILE_MINUTES.get() * 60L * 20L;
        for (WandererEntity member : group()) {
            member.hostile.put(player.getUUID(), until);
            if (member.getTarget() == null) {
                member.setTarget(player);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && player.gameMode.getGameModeForPlayer().isSurvival()) {
            turnOn(player);
        }
        return hurt;
    }

    /** Whom wanderers fight by themselves: monsters, mutants (the robots of the machine mind of later blocks by the tag). */
    public static boolean isFoe(LivingEntity target) {
        return !(target instanceof WandererEntity) && target.isAlive() && (target instanceof Enemy || target instanceof MutantEntity
                || target.getType().is(WandererGroups.FOES));
    }

    // ------------------------------------------------------------------ goals

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ShelterGoal());
        goalSelector.addGoal(2, new RangedGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return !hasGun() && !hasBow() && super.canUse();
            }
        });
        goalSelector.addGoal(4, new FollowLeaderGoal());
        goalSelector.addGoal(5, new TravelGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return isLeader() && campfire != null && super.canUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, WandererEntity.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> p instanceof Player player && hostileTo(player)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, WandererEntity::isFoe));
    }

    /** Whether the group makes camp now: the evening and the night. */
    public boolean campTime() {
        long time = Math.floorMod(level().getDayTime(), DAY);
        return time >= 12500 && time < 23500;
    }

    /** Whether a catastrophe that a person waits out is going on. */
    public boolean stormNow() {
        if (!(level() instanceof ServerLevel server) || !Environment.isEnabled()) {
            return false;
        }
        EnvironmentState state = EnvironmentState.get(server);
        return state.strengthNow() > 0.0f && state.event() != null && !Environment.EMI_STORM.equals(state.event());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (gunCooldown > 0) {
            gunCooldown--;
        }
        if (tickCount % 20 != 0) {
            return;
        }
        if (isLeader()) {
            leaderSecond(server);
        } else if (groupId != null && tickCount % 200 == 0 && group().stream().noneMatch(WandererEntity::isLeader)) {
            leader();
        }
    }

    /** Once a second for the head: the camp, and going away with no player near. */
    private void leaderSecond(ServerLevel server) {
        boolean camp = campTime() && getTarget() == null;
        if (camp && campfire == null && WastelandConfig.WANDERERS_CAMPFIRE.get() && onGround()) {
            BlockPos at = blockPosition();
            if (server.isEmptyBlock(at) && server.getBlockState(at.below()).isFaceSturdy(server, at.below(), net.minecraft.core.Direction.UP)
                    && com.projectrebirth.factions.FactionService.territoryAt(server.getServer(), server, at).isEmpty()) {
                server.setBlockAndUpdate(at, Blocks.CAMPFIRE.defaultBlockState());
                campfire = at;
                getNavigation().stop();
            }
        } else if (!camp && campfire != null) {
            putOutCampfire();
        }
        double radius = WastelandConfig.WANDERERS_NEAR_RADIUS.get();
        if (server.getNearestPlayer(this, radius) == null) {
            alone++;
            if (alone >= WastelandConfig.WANDERERS_DESPAWN_SECONDS.get()) {
                goAway();
            }
        } else {
            alone = 0;
        }
    }

    /** The campfire is taken away (only if it is still the one it lit). */
    public void putOutCampfire() {
        if (campfire != null && level().getBlockState(campfire).getBlock() instanceof CampfireBlock) {
            level().removeBlock(campfire, false);
        }
        campfire = null;
    }

    @Nullable
    public BlockPos campfire() {
        return campfire;
    }

    /** The whole group leaves the world (as the wandering trader does), its campfire with it. */
    public void goAway() {
        putOutCampfire();
        for (WandererEntity member : group()) {
            member.discard();
        }
        discard();
    }

    // ------------------------------------------------------------------ fighting

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack bow = getMainHandItem();
        ItemStack arrows = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrows, power, bow);
        double dx = target.getX() - getX(), dz = target.getZ() - getZ();
        double dy = target.getY(0.33) - arrow.getY();
        arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.6f, 6.0f);
        playSound(bow.getItem() instanceof CrossbowItem ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (getRandom().nextFloat() * 0.4f + 0.8f));
        level().addFreshEntity(arrow);
    }

    /** A shot of the gun of an armor or a robot. Public for tests. */
    public boolean shootGun(LivingEntity target) {
        gunCooldown = WastelandConfig.WANDERERS_GUN_INTERVAL.get();
        triggerAnim("action", "attack");
        return Gunnery.fire(this, getEyePosition().subtract(0.0, 0.2, 0.0), target, 2.5, (float) (WastelandConfig.WANDERERS_GUN_DAMAGE.get() * damageScale()),
                WastelandConfig.WANDERERS_GUN_RANGE.get(), 1.0f, e -> !(e instanceof WandererEntity), net.minecraft.sounds.SoundSource.NEUTRAL);
    }

    /** How the stage of the Wasteland made it stronger (set at its coming). */
    private float damageScale() {
        return getPersistentData().contains("projectrebirth:damage_scale") ? getPersistentData().getFloat("projectrebirth:damage_scale") : 1.0f;
    }

    public void setDamageScale(float scale) {
        getPersistentData().putFloat("projectrebirth:damage_scale", scale);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("action", "attack");
        return super.doHurtTarget(target);
    }

    // ------------------------------------------------------------------ trade

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !isAlive()) {
            return super.mobInteract(player, hand);
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hostileTo(player)) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.wanderers.hostile"), true);
            return InteractionResult.CONSUME;
        }
        if (getTarget() != null) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.wanderers.busy"), true);
            return InteractionResult.CONSUME;
        }
        WandererEntity head = leader();
        if (player instanceof ServerPlayer serverPlayer) {
            WandererTradeMenu.open(serverPlayer, head);
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ death

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !isRemoved()) {
            WandererGroups.leaveRemains(this);
            if (isLeader()) {
                putOutCampfire();
                setLeader(false);
                group().stream().filter(member -> member != this).findFirst().ifPresent(WandererEntity::leader);
            }
        }
        super.die(source);
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("variant", variant());
        tag.putBoolean("leader", isLeader());
        if (groupId != null) {
            tag.putUUID("group", groupId);
        }
        if (campfire != null) {
            tag.putLong("campfire", campfire.asLong());
        }
        ListTag hostileTag = new ListTag();
        hostile.forEach((id, until) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", id);
            entry.putLong("until", until);
            hostileTag.add(entry);
        });
        tag.put("hostile", hostileTag);
        ListTag offerTag = new ListTag();
        for (WandererData.Trade trade : offers) {
            WandererData.Trade.CODEC.encodeStart(registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), trade)
                    .result().ifPresent(offerTag::add);
        }
        tag.put("offers", offerTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getString("variant"));
        setLeader(tag.getBoolean("leader"));
        groupId = tag.hasUUID("group") ? tag.getUUID("group") : null;
        campfire = tag.contains("campfire") ? BlockPos.of(tag.getLong("campfire")) : null;
        hostile.clear();
        for (Tag entry : tag.getList("hostile", Tag.TAG_COMPOUND)) {
            CompoundTag compound = (CompoundTag) entry;
            hostile.put(compound.getUUID("player"), compound.getLong("until"));
        }
        offers.clear();
        for (Tag entry : tag.getList("offers", Tag.TAG_COMPOUND)) {
            WandererData.Trade.CODEC.parse(registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), entry)
                    .result().ifPresent(offers::add);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return kind() == Kind.SURVIVOR ? SoundEvents.PLAYER_HURT : com.projectrebirth.core.ModSounds.ROBOT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return kind() == Kind.SURVIVOR ? SoundEvents.PLAYER_DEATH : SoundEvents.IRON_GOLEM_DEATH;
    }

    // ------------------------------------------------------------------ animation (armor, android, robot)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String prefix = switch (kind()) {
            case ARMORED -> "animation.power_armor.";
            case ANDROID -> "animation.body.";
            default -> "animation.wanderer_robot.";
        };
        RawAnimation idle = RawAnimation.begin().thenLoop(prefix + "idle");
        RawAnimation walk = RawAnimation.begin().thenLoop(prefix + "walk");
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(state.isMoving() ? walk : idle)));
        AnimationController<WandererEntity> action = new AnimationController<>(this, "action", 0, state -> PlayState.STOP);
        if (kind() != Kind.ANDROID) {
            action.triggerableAnim("attack", RawAnimation.begin().thenPlay(prefix + "attack"));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ goal classes

    /** A bow, a crossbow or the gun of an armor or a robot: keeps its distance and shoots. */
    private class RangedGoal extends Goal {
        private int attackTime;

        RangedGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return (hasGun() || hasBow()) && target != null && target.isAlive();
        }

        @Override
        public void start() {
            // synced to clients: an armor raises its weapon to aim
            setAggressive(true);
        }

        @Override
        public void stop() {
            setAggressive(false);
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
            double distance = distanceTo(target);
            boolean sees = getSensing().hasLineOfSight(target);
            getLookControl().setLookAt(target, 30.0f, 30.0f);
            double range = hasGun() ? WastelandConfig.WANDERERS_GUN_RANGE.get() * 0.8 : 14.0;
            if (distance > range || !sees) {
                getNavigation().moveTo(target, 1.0);
            } else if (distance < 5.0) {
                Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(WandererEntity.this, 6, 3, target.position());
                if (away != null) {
                    getNavigation().moveTo(away.x, away.y, away.z, 1.2);
                }
            } else {
                getNavigation().stop();
            }
            if (!sees || distance > range * 1.2) {
                return;
            }
            if (hasGun()) {
                if (gunCooldown <= 0) {
                    shootGun(target);
                }
            } else if (--attackTime <= 0) {
                performRangedAttack(target, 1.0f);
                attackTime = 30;
            }
        }
    }

    /** The others keep near their head; at camp they stay by the fire. */
    private class FollowLeaderGoal extends Goal {
        FollowLeaderGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (isLeader() || getTarget() != null || tickCount % 10 != 0) {
                return false;
            }
            WandererEntity head = leader();
            return head != WandererEntity.this && distanceToSqr(head) > (head.campfire != null ? 16.0 : 36.0);
        }

        @Override
        public void start() {
            WandererEntity head = leader();
            getNavigation().moveTo(head, 1.05);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone() && getTarget() == null;
        }
    }

    /** The head walks the group from place to place; not at camp, in a fight or in a catastrophe. */
    private class TravelGoal extends Goal {
        TravelGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return isLeader() && campfire == null && !campTime() && getTarget() == null && !stormNow() && tickCount % 40 == 0
                    && getNavigation().isDone();
        }

        @Override
        public void start() {
            if (destination == null || destination.distSqr(blockPosition()) < 36.0) {
                destination = WandererGroups.nextDestination((ServerLevel) level(), blockPosition(), getRandom());
            }
            if (destination == null) {
                return;
            }
            // a step of at most 24 blocks toward it: a whole path that long is not searched at once
            Vec3 toward = Vec3.atCenterOf(destination).subtract(position());
            double length = toward.horizontalDistance();
            Vec3 step = length > 24.0 ? position().add(toward.scale(24.0 / length)) : Vec3.atCenterOf(destination);
            BlockPos ground = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(step));
            getNavigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0.9);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone() && getTarget() == null && !stormNow();
        }
    }

    /** In a catastrophe a human looks for a roof near; the machines only stand. */
    private class ShelterGoal extends Goal {
        @Nullable
        private BlockPos roof;

        ShelterGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (kind() != Kind.SURVIVOR || getTarget() != null || tickCount % 20 != 0 || !stormNow() || !level().canSeeSky(blockPosition())) {
                return false;
            }
            for (int i = 0; i < 24; i++) {
                BlockPos at = blockPosition().offset(getRandom().nextInt(25) - 12, getRandom().nextInt(7) - 3, getRandom().nextInt(25) - 12);
                if (level().isEmptyBlock(at) && level().isEmptyBlock(at.above()) && !level().canSeeSky(at)
                        && level().getBlockState(at.below()).isFaceSturdy(level(), at.below(), net.minecraft.core.Direction.UP)) {
                    roof = at;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            getNavigation().moveTo(roof.getX() + 0.5, roof.getY(), roof.getZ() + 0.5, 1.2);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone();
        }
    }

    /** A fresh group member: the stage made it stronger. Public for the spawner and tests. */
    public void scaleByStage(int stage) {
        float health = 1.0f + (float) (WastelandConfig.WANDERERS_HEALTH_PER_STAGE.get() * (stage - 1));
        var attribute = getAttribute(Attributes.MAX_HEALTH);
        attribute.setBaseValue(attribute.getBaseValue() * health);
        setHealth(getMaxHealth());
        float damage = 1.0f + (float) (WastelandConfig.WANDERERS_DAMAGE_PER_STAGE.get() * (stage - 1));
        var attack = getAttribute(Attributes.ATTACK_DAMAGE);
        attack.setBaseValue(attack.getBaseValue() * damage);
        setDamageScale(damage);
    }

    /** Whether it stands in the given box (for tests). */
    public boolean inside(AABB box) {
        return box.contains(position());
    }

    /** The point the head is walking to. */
    @Nullable
    public BlockPos destination() {
        return destination;
    }

    public void setDestination(@Nullable BlockPos destination) {
        this.destination = destination;
    }

    static int clamp(int value, int min, int max) {
        return Mth.clamp(value, min, max);
    }
}
