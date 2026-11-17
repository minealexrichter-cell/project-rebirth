package com.projectrebirth.garrison;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.BodyClass;
import com.projectrebirth.bodies.BodyComponents;
import com.projectrebirth.bodies.BodyGadgets;
import com.projectrebirth.bodies.power.BodyPower;
import com.projectrebirth.bodies.power.ChargingPadBlockEntity;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.powerarmor.PowerArmorRegistry;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An android (1.7.0, block E): an artificial body run by an AI core with a behaviour module. The body is as it was (model,
 * charge, heat, wear, gadgets); on top come a {@link MachineCore} (its structural integrity is the body's integrity, critical a
 * share of it), the rules of targets, the console's orders and its own gear (the main hand is the first slot, the shield the
 * off hand, arrows anywhere). The combat module fights with what it holds: swords, axes, a trident, a bow or a crossbow with
 * arrows from its gear, a shield; never the guns of power armor (rule 1.2). Its body class decides how fast it moves, how soon it
 * answers a target, how well it shoots and how far it sees (config). Low on charge it goes to a charging pad; out of charge it
 * stands still in emergency and the console hears of it. Nobody moves into it while the core is in it: it is not in the
 * register of bodies. The work module (block F) mines, fells trees or farms instead ({@link AndroidWork}); it does not fight, it runs.
 */
public class AndroidEntity extends ArtificialBodyEntity implements GarrisonMachine, RangedAttackMob {
    public static final int GEAR = 41;
    public static final int OFFHAND = 40;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(AndroidEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(AndroidEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> EMERGENCY = SynchedEntityData.defineId(AndroidEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> CRITICAL = SynchedEntityData.defineId(AndroidEntity.class, EntityDataSerializers.FLOAT);

    private MachineCore core;
    private AndroidWork work;
    private ItemStack coreItem = ItemStack.EMPTY;
    private ItemStack moduleItem = ItemStack.EMPTY;
    private final SimpleContainer gear = new SimpleContainer(GEAR);
    private boolean emergency;
    private boolean packing;
    private long targetSince;
    private int attackCooldown;
    @Nullable
    private BlockPos holdPoint;
    private MachineTask lastTask = MachineTask.STAND_DOWN;
    private int routeIndex;

    public AndroidEntity(EntityType<? extends AndroidEntity> type, Level level, BodyClass bodyClass) {
        super(type, level, bodyClass);
        core();
        gear.addListener(container -> syncEquipment());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return ArtificialBodyEntity.createAttributes().add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    private MachineCore core() {
        if (core == null) {
            core = new MachineCore(MachineKind.ANDROID, this::syncCore);
        }
        return core;
    }

    @Override
    public MachineCore machineCore() {
        return core();
    }

    private AndroidWork work() {
        if (work == null) {
            work = new AndroidWork(this);
        }
        return work;
    }

    /** A work android (block F): the work module instead of the combat module. */
    public boolean isWorker() {
        return moduleItem.is(GarrisonRegistry.WORK_MODULE.get());
    }

    /** For tests: whether its work raised this report. */
    public boolean workReported(ConsoleAlarm alarm) {
        return work().raised(alarm);
    }

    /** For tests: whether a block of its mine is a step of the stair it leaves. */
    public boolean isMineStep(BlockPos pos) {
        work().checkOrder();
        return work().isStep(pos);
    }

    /** A blow of its tool, seen by everyone. */
    void workSwing() {
        swing(InteractionHand.MAIN_HAND);
        triggerAnim(MAIN_CONTROLLER, INTERACT_ANIMATION);
    }

    @Override
    protected Component getTypeName() {
        return isWorker() ? Component.translatable(getType().getDescriptionId() + ".work") : super.getTypeName();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, 0);
        builder.define(OWNER, Optional.empty());
        builder.define(EMERGENCY, false);
        builder.define(CRITICAL, 0.0f);
    }

    private void syncCore() {
        if (core == null) {
            return;
        }
        entityData.set(STATE, core.state().ordinal());
        entityData.set(OWNER, Optional.ofNullable(core.owner()));
        entityData.set(CRITICAL, core.critical());
        if (!level().isClientSide && getMaxHealth() > 0) {
            setHealth(Math.max(core.wreck() ? 0.5f : 1.0f, Math.min(getMaxHealth(), core.structural())));
        }
    }

    public MachineCore.State shownState() {
        return MachineCore.State.byId(entityData.get(STATE));
    }

    public Optional<UUID> shownOwner() {
        return entityData.get(OWNER);
    }

    /** Its critical integrity as the server last said (block I: the bars over it). */
    public float shownCritical() {
        return entityData.get(CRITICAL);
    }

    public boolean shownEmergency() {
        return entityData.get(EMERGENCY);
    }

    public SimpleContainer gear() {
        return gear;
    }

    public ItemStack coreItem() {
        return coreItem;
    }

    public ItemStack moduleItem() {
        return moduleItem;
    }

    /** Sets it up from a packed android (server): the core and the module, the owner of the core (or the player), the bars. */
    public void setUp(GarrisonRegistry.AndroidParts parts, @Nullable UUID owner, String ownerName) {
        coreItem = parts.core().copy();
        moduleItem = parts.module().copy();
        core().setOwner(owner, ownerName);
        setOwner(owner);
    }

    /** Whether the AI runs it now: working order and some charge. */
    public boolean working() {
        return core().active() && !emergency;
    }

    @Override
    protected boolean registersAsBody() {
        return false;
    }

    @Override
    protected boolean parked() {
        return level().isClientSide ? shownState() != MachineCore.State.ACTIVE || shownEmergency() : !working();
    }

    // ------------------------------------------------------------------ the limits of its bars

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide) {
            applyLimits();
            syncEquipment();
        }
    }

    /** Its bars from its body class (server; again on every load, so config changes reach it). */
    void applyLimits() {
        float max = (float) RebirthConfig.bodyStats(getBodyClass()).maxHealth().get().doubleValue();
        core().setLimits(max, (float) (max * RebirthConfig.ANDROID_CRITICAL_SHARE.get()));
        core().setEnvironmentByBody(true);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(RebirthConfig.androidStats(getBodyClass()).speed().get());
    }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            dropEverything(false);
            discard();
            return true;
        }
        if (core().wreck() || isInvulnerableTo(source) || invulnerableTime > 10) {
            return false;
        }
        if (amount > 0.0f && isDamageSourceBlocked(source)) {
            hurtCurrentlyUsedShield(amount);
            level().playSound(null, blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 1.0f, 1.0f);
            return false;
        }
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            amount = CombatRules.getDamageAfterAbsorb(this, amount, source, getArmorValue(), (float) getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }
        boolean wasActive = core().active();
        if (!core().hurt(this, source, amount)) {
            return false;
        }
        if (wasActive && !core().active()) {
            playSound(com.projectrebirth.core.ModSounds.GARRISON_MACHINE_SHUTDOWN.get(), 1.0f, 1.0f);
        }
        invulnerableTime = 20;
        hurtTime = 10;
        hurtDuration = 10;
        level().broadcastDamageEvent(this, source);
        playHurtSound(source);
        if (isWorker() && source.getEntity() instanceof LivingEntity) {
            work().attacked();
        } else if (source.getEntity() instanceof LivingEntity attacker && getTarget() == null && TargetRules.mayAttack(this, attacker)) {
            setTarget(attacker);
            targetSince = level().getGameTime();
        }
        if (!working()) {
            getNavigation().stop();
            stopUsingItem();
        }
        return true;
    }

    @Override
    public boolean isDeadOrDying() {
        return false;
    }

    @Override
    protected boolean leavesWreck() {
        return false;
    }

    // ------------------------------------------------------------------ hands

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof TabletItem) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (held.is(PowerArmorRegistry.REPAIR_WRENCH.get())) {
            useWrench(player, held);
            return InteractionResult.CONSUME;
        }
        if (core().wreck()) {
            say(player, "machine.wreck");
            return InteractionResult.CONSUME;
        }
        if (!core().mayManage(player)) {
            say(player, "machine.not_yours");
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            packUp(player);
        } else if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, who) -> new AndroidGearMenu(id, inventory, this),
                    getDisplayName()), buf -> buf.writeVarInt(getId()));
        }
        return InteractionResult.CONSUME;
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.projectrebirth." + key, args), true);
    }

    /** The repair wrench: iron mends structural integrity; with Shift a wreck comes apart. Public for tests. */
    public void useWrench(Player player, ItemStack wrench) {
        if (core().wreck()) {
            if (player.isShiftKeyDown()) {
                dismantle(player);
            } else {
                say(player, "machine.wreck");
            }
            return;
        }
        if (!core().mayManage(player)) {
            say(player, "machine.not_yours");
            return;
        }
        if (player.isShiftKeyDown()) {
            packUp(player);
            return;
        }
        if (core().structural() >= core().maxStructural()) {
            say(player, core().critical() < core().maxCritical() ? "machine.critical_needs_station" : "machine.nothing_to_repair");
            return;
        }
        boolean took = player.isCreative();
        for (int i = 0; !took && i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(Items.IRON_INGOT)) {
                player.getInventory().getItem(i).shrink(1);
                took = true;
            }
        }
        if (!took) {
            say(player, "machine.need_iron");
            return;
        }
        core().repairStructural(RebirthConfig.MACHINE_REPAIR_PER_INGOT.get().floatValue());
        wrench.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        level().playSound(null, blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.4f);
        say(player, "machine.repaired", Mth.ceil(core().structural()), Mth.ceil(core().maxStructural()), Mth.ceil(core().critical()),
                Mth.ceil(core().maxCritical()));
    }

    @Override
    protected void packUp(Player player) {
        if (!isAlive() || !core().mayManage(player)) {
            return;
        }
        setStoredInventory(gearToList());
        gear.clearContent();
        ConsoleLink.packed(this, core());
        if (level() instanceof ServerLevel server) {
            MachineRegistry.get(server.getServer()).remove(getUUID());
        }
        packing = true;
        super.packUp(player);
    }

    @Override
    protected ItemStack packedStack() {
        ItemStack stack = super.packedStack();
        stack.set(GarrisonRegistry.ANDROID_PARTS.get(), new GarrisonRegistry.AndroidParts(coreItem.copy(), moduleItem.copy()));
        stack.set(GarrisonRegistry.PACKED_MACHINE.get(), core().pack());
        if (core().structural() < core().maxStructural()) {
            stack.set(BodyComponents.STORED_INTEGRITY.get(), Math.max(1.0f, core().structural()));
        } else {
            stack.remove(BodyComponents.STORED_INTEGRITY.get());
        }
        return stack;
    }

    /** A wreck taken apart: its gear, its upgrades, its module and a damaged AI core. Public for tests. */
    public void dismantle(@Nullable Player player) {
        if (!isAlive()) {
            return;
        }
        dropEverything(true);
        level().playSound(null, blockPosition(), SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 0.7f, 1.2f);
        if (player != null) {
            say(player, "machine.dismantled");
        }
        discard();
    }

    private void dropEverything(boolean damagedCore) {
        for (int i = 0; i < GEAR; i++) {
            if (!gear.getItem(i).isEmpty()) {
                spawnAtLocation(gear.removeItemNoUpdate(i));
            }
        }
        Upgrades.takeAll(this).forEach(this::spawnAtLocation);
        if (!moduleItem.isEmpty()) {
            spawnAtLocation(moduleItem.copy());
        }
        spawnAtLocation(damagedCore ? new ItemStack(GarrisonRegistry.DAMAGED_AI_CORE.get()) : coreItem.copy());
        moduleItem = ItemStack.EMPTY;
        coreItem = ItemStack.EMPTY;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel server) {
            MachineRegistry.get(server.getServer()).remove(getUUID());
            if (!packing) {
                ConsoleLink.lost(this, core());
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ gear

    /** The hands and the armor show what lies in the gear: the first slot in the main hand, the off-hand slot, the armor slots. */
    void syncEquipment() {
        if (level() == null || level().isClientSide) {
            return;
        }
        ItemStack main = gear.getItem(0);
        setItemSlot(EquipmentSlot.MAINHAND, usable(main) ? main.copy() : ItemStack.EMPTY);
        setItemSlot(EquipmentSlot.OFFHAND, gear.getItem(OFFHAND).copy());
        EquipmentSlot[] armor = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        for (int i = 0; i < 4; i++) {
            setItemSlot(armor[i], gear.getItem(36 + i).copy());
        }
    }

    /** The guns of power armor stay in the gear but never in an android's hand (rule 1.2). */
    public static boolean usable(ItemStack stack) {
        return !(stack.getItem() instanceof com.projectrebirth.weapons.WeaponItem);
    }

    @Override
    protected void dropEquipment() {
        // what it holds is a copy of its gear: the gear itself is dropped where it is due
    }

    private ListTag gearToList() {
        ListTag list = new ListTag();
        HolderLookup.Provider registries = registryAccess();
        for (int i = 0; i < GEAR; i++) {
            ItemStack stack = gear.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag tag = new CompoundTag();
                tag.putByte("Slot", (byte) (i < 36 ? i : i == OFFHAND ? -106 : 100 + (i - 36)));
                list.add(stack.save(registries, tag));
            }
        }
        return list;
    }

    private void listToGear(ListTag list) {
        gear.clearContent();
        for (Tag found : list) {
            CompoundTag tag = (CompoundTag) found;
            int slot = tag.getByte("Slot");
            int index = slot >= 0 && slot < 36 ? slot : slot == -106 ? OFFHAND : slot >= 100 && slot < 104 ? 36 + slot - 100 : -1;
            if (index >= 0) {
                gear.setItem(index, ItemStack.parseOptional(registryAccess(), tag));
            }
        }
    }

    private boolean hasRangedWeapon() {
        ItemStack main = getMainHandItem();
        return (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) && !arrows().isEmpty();
    }

    private ItemStack arrows() {
        for (int i = 0; i < GEAR; i++) {
            if (gear.getItem(i).getItem() instanceof ArrowItem) {
                return gear.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ time

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (tickCount % 20 == 0) {
            second();
        }
    }

    private void second() {
        MachineCore machine = core();
        machine.second(this, upgrades());
        if (machine.active() && BodyPower.enabled()) {
            double use = RebirthConfig.ANDROID_ENERGY_ACTIVE.get() + (getDeltaMovement().horizontalDistanceSqr() > 1.0e-4
                    ? RebirthConfig.ANDROID_ENERGY_MOVING.get() : 0.0);
            setCharge(charge() - (float) (use * BodyPower.useMultiplier(getBodyClass())));
        }
        boolean empty = BodyPower.enabled() && charge() <= 0.0f;
        boolean recovered = charge() > BodyPower.capacity(this) * 0.05f;
        if (empty && !emergency || emergency && recovered) {
            emergency = empty;
            entityData.set(EMERGENCY, emergency);
            ConsoleLink.supply(this, machine, emergency);
            if (emergency) {
                getNavigation().stop();
                setTarget(null);
                playSound(com.projectrebirth.core.ModSounds.GARRISON_MACHINE_SHUTDOWN.get(), 1.0f, 0.9f);
            }
        }
        double speed = RebirthConfig.androidStats(getBodyClass()).speed().get() * (machine.emiFault() ? 0.6 : 1.0);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
        if (!machine.task().equals(lastTask)) {
            lastTask = machine.task();
            holdPoint = blockPosition();
            routeIndex = 0;
        }
        syncEquipment();
    }

    // ------------------------------------------------------------------ the mind

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ChargeGoal());
        goalSelector.addGoal(2, new FightGoal());
        goalSelector.addGoal(2, work().new WorkGoal());
        goalSelector.addGoal(3, new TaskGoal());
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new ChooseTargetGoal());
    }

    private RebirthConfig.AndroidStats stats() {
        return RebirthConfig.androidStats(getBodyClass());
    }

    /** Whether a target is still one to fight under the current order (guard: near the point; escort: near the one escorted). */
    private boolean withinOrder(LivingEntity target) {
        MachineTask task = core().task();
        if (task.type() == MachineTask.Type.GUARD && task.point().isPresent()) {
            double radius = RebirthConfig.ANDROID_GUARD_RADIUS.get();
            return target.blockPosition().distSqr(task.point().get()) <= radius * radius;
        }
        return true;
    }

    private boolean validTarget(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target.level() != level()) {
            return false;
        }
        double sight = stats().sight().get();
        return distanceToSqr(target) <= sight * sight * 1.5 && TargetRules.mayAttack(this, target) && withinOrder(target);
    }

    /** Chooses the nearest target it may fight, now and then. */
    private final class ChooseTargetGoal extends Goal {
        private int wait;

        ChooseTargetGoal() {
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!working() || isWorker() || --wait > 0) {
                return false;
            }
            wait = RebirthConfig.ANDROID_THINK_TICKS.get();
            if (validTarget(getTarget())) {
                return false;
            }
            if (getTarget() != null) {
                setTarget(null);
            }
            double sight = stats().sight().get();
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (LivingEntity found : level().getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(sight),
                    found -> found != AndroidEntity.this && found.isAlive())) {
                double distance = distanceToSqr(found);
                if (distance < bestDistance && distance <= sight * sight && hasLineOfSight(found) && validTarget(found)) {
                    best = found;
                    bestDistance = distance;
                }
            }
            if (best != null) {
                setTarget(best);
                targetSince = level().getGameTime();
                playSound(com.projectrebirth.core.ModSounds.GARRISON_TARGET_LOCK.get(), 0.8f, 1.1f);
            }
            return false;
        }
    }

    /** Fights the target with what it holds: shoots from afar with a bow or a crossbow and arrows, strikes up close otherwise. */
    private final class FightGoal extends Goal {
        FightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return working() && !isWorker() && validTarget(getTarget());
        }

        @Override
        public void stop() {
            getNavigation().stop();
            stopUsingItem();
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
            double distance = distanceToSqr(target);
            boolean seen = getSensing().hasLineOfSight(target);
            boolean ready = level().getGameTime() - targetSince >= stats().reaction().get() && attackCooldown <= 0;
            boolean holding = core().task().type() == MachineTask.Type.HOLD && holdPoint != null;
            double leash = RebirthConfig.ANDROID_HOLD_RADIUS.get();
            boolean mayChase = !holding || target.blockPosition().distSqr(holdPoint) <= (leash + 2) * (leash + 2);
            if (hasRangedWeapon()) {
                if ((distance > 144.0 || !seen) && mayChase) {
                    getNavigation().moveTo(target, 1.0);
                } else {
                    getNavigation().stop();
                }
                if (seen && distance <= 400.0 && ready) {
                    performRangedAttack(target, 1.0f);
                    attackCooldown = getMainHandItem().getItem() instanceof CrossbowItem ? 35 : 25;
                }
                return;
            }
            if (mayChase) {
                getNavigation().moveTo(target, 1.15);
            } else {
                getNavigation().stop();
            }
            boolean shield = getOffhandItem().getItem() instanceof ShieldItem;
            if (shield && distance < 25.0 && attackCooldown > 6 && !isUsingItem()) {
                startUsingItem(InteractionHand.OFF_HAND);
            } else if (shield && isUsingItem() && attackCooldown <= 2) {
                stopUsingItem();
            }
            if (ready && isWithinMeleeAttackRange(target)) {
                stopUsingItem();
                swing(InteractionHand.MAIN_HAND);
                triggerAnim(MAIN_CONTROLLER, INTERACT_ANIMATION);
                doHurtTarget(target);
                setCharge(charge() - (float) (RebirthConfig.BODY_ENERGY_ATTACK.get() * BodyPower.useMultiplier(getBodyClass())));
                attackCooldown = stats().meleeCooldown().get();
            }
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack weapon = getMainHandItem();
        ItemStack ammo = arrows();
        if (ammo.isEmpty() || !(ammo.getItem() instanceof ArrowItem arrowItem)) {
            return;
        }
        AbstractArrow arrow = arrowItem.createArrow(level(), ammo.copyWithCount(1), this, weapon);
        double dx = target.getX() - getX();
        double dy = target.getY(0.33) - arrow.getY();
        double dz = target.getZ() - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        boolean crossbow = weapon.getItem() instanceof CrossbowItem;
        arrow.shoot(dx, dy + flat * (crossbow ? 0.1 : 0.2), dz, crossbow ? 3.0f : 1.6f, stats().inaccuracy().get().floatValue());
        level().addFreshEntity(arrow);
        playSound(crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (getRandom().nextFloat() * 0.4f + 0.8f));
        ammo.shrink(1);
        gear.setChanged();
        setCharge(charge() - (float) (RebirthConfig.BODY_ENERGY_ATTACK.get() * BodyPower.useMultiplier(getBodyClass())));
    }

    /** Low on charge: to the nearest charging pad or service post (block H), and stays on it until charged. */
    private final class ChargeGoal extends Goal {
        @Nullable
        private Vec3 pad;
        private int wait;

        ChargeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!core().active() || !BodyPower.enabled() || charge() > BodyPower.capacity(AndroidEntity.this) * RebirthConfig.ANDROID_LOW_CHARGE.get()
                    || --wait > 0) {
                return false;
            }
            // canUse runs every other tick: about once a second
            wait = 10;
            BlockPos found = nearestPad();
            Vec3 post = level() instanceof ServerLevel server ? ServicePostBlockEntity.nearest(server, blockPosition()) : null;
            pad = found == null ? post : post != null && post.distanceToSqr(position()) < found.distToCenterSqr(position())
                    ? post : Vec3.atBottomCenterOf(found).add(0, 0.2, 0);
            return pad != null;
        }

        @Override
        public boolean canContinueToUse() {
            return pad != null && core().active() && charge() < BodyPower.capacity(AndroidEntity.this) * RebirthConfig.ANDROID_CHARGED.get()
                    && (level().getBlockEntity(BlockPos.containing(pad)) instanceof ChargingPadBlockEntity
                    || level().getBlockEntity(BlockPos.containing(pad).below()) instanceof ChargingPadBlockEntity
                    || level().getBlockState(BlockPos.containing(pad).below()).is(GarrisonRegistry.SERVICE_POST_PLATE.get()));
        }

        @Override
        public void tick() {
            if (pad != null && distanceToSqr(pad) > 0.5) {
                getNavigation().moveTo(pad.x, pad.y, pad.z, 1.0);
            } else {
                getNavigation().stop();
            }
        }
    }

    /** The nearest charging pad within the config's radius, or null. Public for tests. */
    @Nullable
    public BlockPos nearestPad() {
        return level() instanceof ServerLevel server ? nearestPad(server, blockPosition()) : null;
    }

    /** The nearest charging pad to a place within the config's radius, or null (1.7.1: the small robot looks for one too). */
    @Nullable
    public static BlockPos nearestPad(ServerLevel server, BlockPos from) {
        int radius = RebirthConfig.ANDROID_PAD_RADIUS.get();
        int chunks = (radius >> 4) + 1;
        BlockPos best = null;
        double bestDistance = (double) radius * radius;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                LevelChunk chunk = server.getChunkSource().getChunkNow((from.getX() >> 4) + dx, (from.getZ() >> 4) + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity found : chunk.getBlockEntities().values()) {
                    double distance = found.getBlockPos().distSqr(from);
                    if (found instanceof ChargingPadBlockEntity && distance < bestDistance) {
                        best = found.getBlockPos();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** Follows its order when there is nothing to fight: guard, patrol, escort, hold, go to the post or take cover, stand down. */
    private final class TaskGoal extends Goal {
        private Vec3 lastPlace = Vec3.ZERO;
        private int stuck;

        TaskGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return working() && getTarget() == null && !work().active();
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
            double near = core().task().type() == MachineTask.Type.ESCORT ? 9.0 : 2.25;
            if (distanceToSqr(goal) <= near) {
                getNavigation().stop();
                if (core().task().type() == MachineTask.Type.PATROL && !core().task().route().isEmpty()) {
                    routeIndex = (routeIndex + 1) % core().task().route().size();
                }
                return;
            }
            if (tickCount % 10 == 0) {
                getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
            }
            if (tickCount % 40 == 0) {
                stuck = position().distanceToSqr(lastPlace) < 0.04 ? stuck + 1 : 0;
                lastPlace = position();
                if (stuck >= 1 && BodyGadgets.has(registryAccess(), upgrades(), BodyGadgets.JETPACK) && goal.y > getY() - 0.5) {
                    // a jetpack lifts it over what stands in the way
                    Vec3 toward = goal.subtract(position()).multiply(1, 0, 1).normalize().scale(0.3);
                    setDeltaMovement(toward.x, 0.8, toward.z);
                    hasImpulse = true;
                    setCharge(charge() - (float) (RebirthConfig.BODY_ENERGY_JETPACK.get() * 0.5));
                    if (level() instanceof ServerLevel server) {
                        server.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 8, 0.2, 0.1, 0.2, 0.02);
                    }
                }
            }
        }

        @Nullable
        private Vec3 goal() {
            MachineTask task = core().task();
            return switch (task.type()) {
                case GUARD -> task.point().map(Vec3::atBottomCenterOf).orElse(null);
                case HOLD -> holdPoint == null ? null : Vec3.atBottomCenterOf(holdPoint);
                case PATROL -> task.route().isEmpty() ? null
                        : Vec3.atBottomCenterOf(task.route().get(Math.min(routeIndex, task.route().size() - 1)).above());
                case ESCORT -> task.escort().map(id -> level().getPlayerByUUID(id)).filter(p -> p.distanceToSqr(AndroidEntity.this) < 64 * 64)
                        .map(Player::position).orElse(null);
                case TO_POST, SHELTER -> GuardMechEntity.postOrConsole(AndroidEntity.this, core());
                default -> null;
            };
        }
    }

    // ------------------------------------------------------------------ the look

    private static final software.bernie.geckolib.animation.RawAnimation DOWN =
            software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.body.crouch");

    private static final software.bernie.geckolib.animation.RawAnimation SHUTDOWN =
            software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.body.shutdown");
    private static final software.bernie.geckolib.animation.RawAnimation WRECKED =
            software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.body.wrecked");

    /**
     * It walks, runs after a target, crouches out of charge (emergency mode). 1.7.1, block J: shut down (no structural integrity)
     * it sinks onto its knees, a wreck falls on its face; both stay so.
     */
    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, MAIN_CONTROLLER, 5, state -> {
            if (shownState() == MachineCore.State.WRECK) {
                return state.setAndContinue(WRECKED);
            }
            if (shownState() == MachineCore.State.DISABLED) {
                return state.setAndContinue(SHUTDOWN);
            }
            if (shownEmergency()) {
                return state.setAndContinue(DOWN);
            }
            if (state.isMoving()) {
                return state.setAndContinue(isAggressive() ? RUN : WALK);
            }
            return state.setAndContinue(IDLE);
        }).triggerableAnim(INTERACT_ANIMATION, software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.body.interact")));
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        setAggressive(target != null);
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        core().save(tag);
        if (!coreItem.isEmpty()) {
            tag.put("AiCore", coreItem.save(registryAccess()));
        }
        if (!moduleItem.isEmpty()) {
            tag.put("Module", moduleItem.save(registryAccess()));
        }
        tag.put("Gear", gearToList());
        tag.putBoolean("Emergency", emergency);
        work().save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        core().load(tag);
        coreItem = tag.contains("AiCore") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("AiCore")) : ItemStack.EMPTY;
        moduleItem = tag.contains("Module") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("Module")) : ItemStack.EMPTY;
        listToGear(tag.getList("Gear", Tag.TAG_COMPOUND));
        emergency = tag.getBoolean("Emergency");
        work().load(tag);
        entityData.set(EMERGENCY, emergency);
        setOwner(core().owner());
    }

    /** For tests: the gear as the list a packed body drops. */
    List<ItemStack> gearList() {
        return gear.getItems();
    }
}
