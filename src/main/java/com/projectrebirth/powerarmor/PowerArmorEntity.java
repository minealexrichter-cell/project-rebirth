package com.projectrebirth.powerarmor;

import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.projectrebirth.ProjectRebirth;
import com.projectrebirth.environment.MachineBody;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.core.Parked;
import com.projectrebirth.factions.FactionService;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.UpgradeLayout;
import com.projectrebirth.upgrades.Upgrades;
import com.projectrebirth.weapons.Weapons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A power armor: a machine standing in the world that one player can climb into and drive.
 * <p>
 * It has two independent integrity values instead of vanilla health:
 * <ul>
 *   <li>structural: takes all damage, regenerates slowly; at 0 the armor shuts down and locks the pilot in;</li>
 *   <li>critical: drops mainly from explosions and fire, never regenerates; at 0 the armor becomes a wreck.</li>
 * </ul>
 * A shut-down armor is left by blowing the hatch from inside (costs critical integrity), by a rescue tool
 * from outside, or it starts again after being repaired with the repair wrench.
 */
public class PowerArmorEntity extends PathfinderMob implements GeoEntity, PlayerRideableJumping, UpgradeHost {
    public static final byte STATE_ACTIVE = 0;
    public static final byte STATE_DISABLED = 1;
    public static final byte STATE_WRECKED = 2;

    private static final EntityDataAccessor<Float> STRUCTURAL = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CRITICAL = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_STRUCTURAL = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_CRITICAL = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> HATCH_PROGRESS = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> VARIANT = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BYTE);
    /** Worker armor: true = 3x3 mining, false = one block. */
    private static final EntityDataAccessor<Boolean> WIDE_MINING = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<InstalledUpgrades> UPGRADES = SynchedEntityData.defineId(PowerArmorEntity.class, InstalledUpgrades.ENTITY_DATA);
    private static final EntityDataAccessor<Boolean> FLOODLIGHT_ON = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> AIMING_WEAPON = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> WEAPON_RELOAD_END = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.LONG);
    /** The shell that opens to let the pilot in and out (1.5.2): one of the SHELL_ states. */
    private static final EntityDataAccessor<Byte> SHELL = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BYTE);
    /** The worker is breaking a block (1.5.2): its arms hammer like a jackhammer for everybody to see. */
    private static final EntityDataAccessor<Boolean> MINING = SynchedEntityData.defineId(PowerArmorEntity.class, EntityDataSerializers.BOOLEAN);
    /** Without a swing of the pilot for this long, the mining stops (a creative break sends no "stopped"). */
    private static final int MINING_GRACE_TICKS = 10;
    /** The pilot's seat in the cab, over the armor's feet, in blocks: up, and back from the middle. See PowerArmorClient.CAB_PILOT_SCALE. */
    public static final double CAB_SEAT_HEIGHT = 1.1875;
    public static final double CAB_SEAT_BACK = 0.075;
    public static final byte SHELL_CLOSED = 0;
    public static final byte SHELL_OPENING = 1;
    public static final byte SHELL_OPEN = 2;
    public static final byte SHELL_CLOSING = 3;
    /** The lengths of the open and close animations, and how long it stays open while the pilot steps in or out. */
    public static final int SHELL_OPEN_TICKS = 24;
    public static final int SHELL_HOLD_TICKS = 6;
    public static final int SHELL_CLOSE_TICKS = 20;
    /** How far the player who asked to get in may walk away before the armor gives up and closes again. */
    private static final double SHELL_ENTER_DISTANCE = 4.0;

    private static final ResourceLocation PILOT_ATTACK_MODIFIER = ProjectRebirth.id("power_armor_attack");
    private static final ResourceLocation PILOT_KNOCKBACK_MODIFIER = ProjectRebirth.id("power_armor_knockback");
    private static final ResourceLocation PILOT_ENTITY_REACH_MODIFIER = ProjectRebirth.id("power_armor_entity_reach");
    private static final ResourceLocation PILOT_BLOCK_REACH_MODIFIER = ProjectRebirth.id("power_armor_block_reach");
    public static final String ACTION_CONTROLLER = "action";
    public static final String ATTACK_ANIMATION = "attack";
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.power_armor.attack");

    /**
     * Set by the client: true for the player of this game client. The pilot looks out from inside the armor's
     * hitbox, so on their own client the armor must not catch their clicks on blocks and entities.
     */
    public static Predicate<Entity> isLocalPlayer = entity -> false;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.power_armor.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.power_armor.walk");
    private static final RawAnimation MINE = RawAnimation.begin().thenLoop("animation.power_armor.mine");
    private static final RawAnimation SHUTDOWN = RawAnimation.begin().thenPlayAndHold("animation.power_armor.shutdown");
    private static final RawAnimation WRECKED = RawAnimation.begin().thenPlayAndHold("animation.power_armor.wrecked");
    private static final RawAnimation SHELL_OPEN_ANIMATION = RawAnimation.begin().thenPlayAndHold("animation.power_armor.open");
    private static final RawAnimation SHELL_CLOSE_ANIMATION = RawAnimation.begin().thenPlay("animation.power_armor.close");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID owner;
    /** Where this armor's floodlight stands now; only on the server. */
    @Nullable
    private BlockPos floodlightPos;
    /** False only for an armor that has never been in a level; then it starts at full integrity. */
    private boolean initialized;
    private int ticksSinceDamage;
    private boolean hatchHeld;
    private int hatchTicks;
    /** True while the armor itself lets the pilot out, so the shutdown lock does not block it. */
    private boolean releasingPilot;
    /** 0..1 jump strength requested by the pilot (charged with the jump key), applied when on the ground. */
    private float pendingJump;
    private int lastAttackAnimationTick;
    private int lastMiningTick;
    /** Server only: ticks left in the current shell state, and what happens when it is open. Not saved: a loaded armor is shut. */
    private int shellTicks;
    @Nullable
    private Player enteringPlayer;
    private boolean pilotLeaving;

    public PowerArmorEntity(EntityType<? extends PowerArmorEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.12)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STRUCTURAL, 0.0f);
        builder.define(CRITICAL, 0.0f);
        builder.define(MAX_STRUCTURAL, 1.0f);
        builder.define(MAX_CRITICAL, 1.0f);
        builder.define(STATE, STATE_ACTIVE);
        builder.define(HATCH_PROGRESS, 0.0f);
        builder.define(VARIANT, (byte) ArmorVariant.COMBAT.ordinal());
        builder.define(WIDE_MINING, true);
        builder.define(UPGRADES, InstalledUpgrades.EMPTY);
        builder.define(FLOODLIGHT_ON, false);
        builder.define(AIMING_WEAPON, false);
        builder.define(WEAPON_RELOAD_END, 0L);
        builder.define(SHELL, SHELL_CLOSED);
        builder.define(MINING, false);
    }

    public boolean isMining() {
        return entityData.get(MINING);
    }

    /** Server: the pilot starts or stops breaking a block. Only the worker shows it. */
    public void setMining(boolean mining) {
        if (mining) {
            lastMiningTick = tickCount;
        }
        if (isMining() != mining && (!mining || getVariant() == ArmorVariant.WORKER)) {
            entityData.set(MINING, mining);
        }
    }

    private void tickMining(Player pilot) {
        if (!isMining()) {
            return;
        }
        if (pilot == null || !isActive()) {
            setMining(false);
            return;
        }
        if (pilot.swinging) {
            lastMiningTick = tickCount;
        } else if (tickCount - lastMiningTick > MINING_GRACE_TICKS) {
            setMining(false);
        }
    }

    // ---------------------------------------------------------------- state

    public float getStructural() {
        return entityData.get(STRUCTURAL);
    }

    public float getCritical() {
        return entityData.get(CRITICAL);
    }

    public float getMaxStructural() {
        return entityData.get(MAX_STRUCTURAL);
    }

    public float getMaxCritical() {
        return entityData.get(MAX_CRITICAL);
    }

    public ArmorVariant getVariant() {
        ArmorVariant[] variants = ArmorVariant.values();
        return variants[Mth.clamp(entityData.get(VARIANT), 0, variants.length - 1)];
    }

    /** Set before the armor joins the level (placement from the item); its stats follow from it. */
    public void setVariant(ArmorVariant variant) {
        entityData.set(VARIANT, (byte) variant.ordinal());
    }

    public RebirthConfig.ArmorStats stats() {
        return RebirthConfig.armorStats(getVariant());
    }

    public boolean isWideMining() {
        return entityData.get(WIDE_MINING);
    }

    public void setWideMining(boolean wide) {
        entityData.set(WIDE_MINING, wide);
    }

    /** 0..1 while the pilot holds the emergency hatch key in a shut-down armor. */
    public float getHatchProgress() {
        return entityData.get(HATCH_PROGRESS);
    }

    public boolean isActive() {
        return entityData.get(STATE) == STATE_ACTIVE;
    }

    public boolean isDisabled() {
        return entityData.get(STATE) == STATE_DISABLED;
    }

    public boolean isWrecked() {
        return entityData.get(STATE) == STATE_WRECKED;
    }

    // ---------------------------------------------------------------- the opening shell (1.5.2)

    public byte shellState() {
        return entityData.get(SHELL);
    }

    /** Whether the shell is shut: the armor walks and hides its pilot only then. */
    public boolean isShellClosed() {
        return shellState() == SHELL_CLOSED;
    }

    /** Whether getting in and out goes through the opening shell, for this armor's model and the server's settings. */
    public boolean opensShell() {
        return getVariant().opensShell() && RebirthConfig.ARMOR_OPENING_SHELL.get();
    }

    /** True while the armor itself lets the pilot out. */
    public boolean isReleasingPilot() {
        return releasingPilot;
    }

    private void setShell(byte state, int ticks) {
        entityData.set(SHELL, state);
        shellTicks = ticks;
    }

    private void shellSound(boolean opening) {
        level().playSound(null, blockPosition(), opening ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0f, 0.6f);
        level().playSound(null, blockPosition(), opening ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, SoundSource.NEUTRAL, 0.6f, 0.7f);
    }

    /** Opens the shell for a player who wants to get in; they step in when it is open. Public for tests. */
    public void openFor(Player player) {
        enteringPlayer = player;
        pilotLeaving = false;
        setShell(SHELL_OPENING, SHELL_OPEN_TICKS);
        shellSound(true);
        say(player, "opening");
    }

    /**
     * Called when the pilot asks to get out (sneak): opens the shell, and the pilot steps out when it is open. False if the
     * armor does not open this way now; then the normal way out applies.
     */
    public boolean leaveThroughShell(Player pilot) {
        if (!opensShell() || !isActive() || getPilot() != pilot) {
            return false;
        }
        if (isShellClosed()) {
            enteringPlayer = null;
            pilotLeaving = true;
            setShell(SHELL_OPENING, SHELL_OPEN_TICKS);
            shellSound(true);
        }
        // already opening or closing: the request is taken, the pilot waits
        return true;
    }

    /** One tick of the shell, on the server: open, let in or out, close. Public for tests (mock armors tick by themselves). */
    public void tickShell() {
        byte state = shellState();
        if (state == SHELL_CLOSED || --shellTicks > 0) {
            return;
        }
        if (state == SHELL_OPENING) {
            setShell(SHELL_OPEN, SHELL_HOLD_TICKS);
        } else if (state == SHELL_OPEN) {
            if (pilotLeaving) {
                pilotLeaving = false;
                releasePilot();
            } else if (enteringPlayer != null) {
                Player player = enteringPlayer;
                enteringPlayer = null;
                if (player != null && player.isAlive() && !player.isRemoved() && player.level() == level() && !player.isPassenger() && !isVehicle() && isActive() && canBeUsedBy(player)
                        && player.distanceTo(this) <= SHELL_ENTER_DISTANCE && player.startRiding(this)) {
                    player.displayClientMessage(Component.translatable("message.projectrebirth.armor.entered",
                            Mth.ceil(getStructural()), Mth.ceil(getMaxStructural()), Mth.ceil(getCritical()), Mth.ceil(getMaxCritical())), true);
                }
            }
            setShell(SHELL_CLOSING, SHELL_CLOSE_TICKS);
            shellSound(false);
        } else {
            setShell(SHELL_CLOSED, 0);
        }
    }

    /** The pilot stands in the shell rather than sitting: seen only while it is open. In the worker's cab the pilot sits. */
    @Override
    public boolean shouldRiderSit() {
        return getVariant().hasCab();
    }

    /** A shut-down armor keeps its pilot inside unless the armor itself is letting them out. */
    public boolean isPilotLocked() {
        return isDisabled() && !releasingPilot;
    }

    @Nullable
    public Player getPilot() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Nullable
    public UUID getOwnerId() {
        return owner;
    }

    @Override
    public InstalledUpgrades upgrades() {
        return entityData.get(UPGRADES);
    }

    @Override
    public void setUpgrades(InstalledUpgrades upgrades) {
        entityData.set(UPGRADES, upgrades);
        if (!level().isClientSide && initialized) {
            applyConfig();
        }
    }

    /** The pilot is aiming a weapon. Decided by the server (see Weapons), seen by everyone. */
    public boolean isAimingWeapon() {
        return entityData.get(AIMING_WEAPON);
    }

    /** The game time at which the current reload step ends; 0 without a reload. */
    public long weaponReloadEnd() {
        return entityData.get(WEAPON_RELOAD_END);
    }

    public void setWeaponPose(boolean aiming, long reloadEnd) {
        entityData.set(AIMING_WEAPON, aiming);
        entityData.set(WEAPON_RELOAD_END, reloadEnd);
    }

    public boolean isFloodlightOn() {
        return entityData.get(FLOODLIGHT_ON);
    }

    public void setFloodlightOn(boolean on) {
        entityData.set(FLOODLIGHT_ON, on);
    }

    @Override
    public UpgradeLayout layout() {
        return UpgradeLayout.ArmorParts.INSTANCE;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    public boolean canBeUsedBy(Player player) {
        return owner == null || owner.equals(player.getUUID());
    }

    /** Sets both integrities (clamped) and updates the state. Used by placement from an item and by tests. */
    public void setIntegrity(float structural, float critical) {
        entityData.set(STRUCTURAL, Mth.clamp(structural, 0.0f, getMaxStructural()));
        entityData.set(CRITICAL, Mth.clamp(critical, 0.0f, getMaxCritical()));
        if (getCritical() <= 0.0f) {
            becomeWreck();
        } else if (getStructural() <= 0.0f && isActive()) {
            shutDown();
        }
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide) {
            applyConfig();
        }
    }

    /** Re-applied on every load so that config changes reach existing armors. */
    private void applyConfig() {
        entityData.set(MAX_STRUCTURAL, stats().maxStructural().get().floatValue() + ArmorUpgradeEffects.structuralBonus(registryAccess(), upgrades()));
        entityData.set(MAX_CRITICAL, stats().maxCritical().get().floatValue() + ArmorUpgradeEffects.criticalBonus(registryAccess(), upgrades()));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(stats().movementSpeed().get());
        getAttribute(Attributes.SAFE_FALL_DISTANCE).setBaseValue(ArmorUpgradeEffects.safeFallDistance(this));
        if (!initialized) {
            entityData.set(STRUCTURAL, getMaxStructural());
            entityData.set(CRITICAL, getMaxCritical());
            initialized = true;
        }
        entityData.set(STRUCTURAL, Math.min(getStructural(), getMaxStructural()));
        entityData.set(CRITICAL, Math.min(getCritical(), getMaxCritical()));
    }

    // ---------------------------------------------------------------- damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            // /kill and the void remove the armor completely; the pilot is put out first.
            releasePilot();
            Upgrades.takeAll(this).forEach(this::spawnAtLocation);
            discard();
            return true;
        }
        if (isWrecked() || isInvulnerableTo(source) || invulnerableTime > 10) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && hasPassenger(attacker)) {
            return false;
        }
        // This hurt() does not go through the usual damage event, so friendly fire is checked here directly.
        if (FactionService.blocksDamage(this, source)) {
            return false;
        }
        float structuralLoss = amount * RebirthConfig.ARMOR_STRUCTURAL_DAMAGE_MULTIPLIER.get().floatValue();
        float criticalLoss = amount * criticalMultiplier(source);
        entityData.set(STRUCTURAL, Math.max(0.0f, getStructural() - structuralLoss));
        entityData.set(CRITICAL, Math.max(0.0f, getCritical() - criticalLoss));
        invulnerableTime = 20;
        ticksSinceDamage = 0;
        level().broadcastDamageEvent(this, source);
        playSound(ModSounds.ARMOR_HURT.get(), 1.0f, 1.0f);

        if (getCritical() <= 0.0f) {
            becomeWreck();
        } else if (getStructural() <= 0.0f && isActive()) {
            shutDown();
        }
        return true;
    }

    private static float criticalMultiplier(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return RebirthConfig.ARMOR_CRITICAL_EXPLOSION_MULTIPLIER.get().floatValue();
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return RebirthConfig.ARMOR_CRITICAL_FIRE_MULTIPLIER.get().floatValue();
        }
        return RebirthConfig.ARMOR_CRITICAL_OTHER_MULTIPLIER.get().floatValue();
    }

    private void shutDown() {
        entityData.set(STATE, STATE_DISABLED);
        entityData.set(HATCH_PROGRESS, 0.0f);
        hatchTicks = 0;
        level().playSound(null, blockPosition(), ModSounds.ARMOR_SHUTDOWN.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
        Player pilot = getPilot();
        if (pilot != null) {
            pilot.sendSystemMessage(Component.translatable("message.projectrebirth.armor.shutdown"));
        }
    }

    private void restart() {
        entityData.set(STATE, STATE_ACTIVE);
        entityData.set(HATCH_PROGRESS, 0.0f);
        hatchTicks = 0;
        level().playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.0f, 1.2f);
        Player pilot = getPilot();
        if (pilot != null) {
            pilot.sendSystemMessage(Component.translatable("message.projectrebirth.armor.restarted"));
        }
    }

    private void becomeWreck() {
        if (isWrecked()) {
            return;
        }
        entityData.set(STATE, STATE_WRECKED);
        setShell(SHELL_CLOSED, 0);
        enteringPlayer = null;
        pilotLeaving = false;
        entityData.set(STRUCTURAL, 0.0f);
        entityData.set(CRITICAL, 0.0f);
        entityData.set(HATCH_PROGRESS, 0.0f);
        Player pilot = getPilot();
        releasePilot();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.2, getZ(), 1, 0, 0, 0, 0);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.5, getZ(), 30, 0.5, 0.8, 0.5, 0.02);
            serverLevel.playSound(null, blockPosition(), ModSounds.ARMOR_WRECKED.get(), SoundSource.NEUTRAL, 1.2f, 1.0f);
        }
        if (pilot != null) {
            pilot.sendSystemMessage(Component.translatable("message.projectrebirth.armor.wrecked"));
            float damage = RebirthConfig.ARMOR_WRECK_PILOT_DAMAGE.get().floatValue();
            if (damage > 0) {
                pilot.hurt(damageSources().explosion(this, null), damage);
            }
        }
    }

    /** Puts the pilot out next to the armor, even if the armor is shut down. */
    public void releasePilot() {
        releasingPilot = true;
        try {
            ejectPassengers();
        } finally {
            releasingPilot = false;
        }
        hatchHeld = false;
        hatchTicks = 0;
        entityData.set(HATCH_PROGRESS, 0.0f);
    }

    // ---------------------------------------------------------------- tick

    /** Unloaded with its chunk, packed, destroyed: the light goes with the armor. */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide) {
            Floodlight.clear(level(), floodlightPos);
            floodlightPos = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (isParked()) {
            Parked.holdRotation(this);
        }
        if (level().isClientSide) {
            return;
        }
        if (Floodlight.timeToUpdate(this)) {
            floodlightPos = Floodlight.update(this, floodlightPos);
        }
        Player pilot = getPilot();
        if (pilot == null && (isAimingWeapon() || weaponReloadEnd() != 0L)) {
            setWeaponPose(false, 0L);
        }
        if (pilot != null && !isWrecked()) {
            // The cockpit is sealed.
            pilot.setAirSupply(pilot.getMaxAirSupply());
        }
        if (isActive()) {
            ticksSinceDamage++;
            float regen = RebirthConfig.ARMOR_STRUCTURAL_REGEN_PER_SECOND.get().floatValue();
            if (regen > 0 && tickCount % 20 == 0 && getStructural() < getMaxStructural()
                    && ticksSinceDamage >= RebirthConfig.ARMOR_STRUCTURAL_REGEN_DELAY_SECONDS.get() * 20) {
                entityData.set(STRUCTURAL, Math.min(getMaxStructural(), getStructural() + regen));
            }
        }
        tickHatch(pilot);
        tickShell();
        tickMining(pilot);
        if (tickCount % 20 == 0) {
            MachineBody.tickArmor(this);
        }
    }

    /** Acid eats the outer shell: structural integrity only, no sound and no wreck. */
    public void corrode(float amount) {
        if (isWrecked() || amount <= 0.0f) {
            return;
        }
        entityData.set(STRUCTURAL, Math.max(0.0f, getStructural() - amount));
        ticksSinceDamage = 0;
        if (getStructural() <= 0.0f && isActive()) {
            shutDown();
        }
    }

    private void tickHatch(@Nullable Player pilot) {
        if (!isDisabled() || pilot == null || !hatchHeld) {
            if (hatchTicks != 0) {
                hatchTicks = 0;
                entityData.set(HATCH_PROGRESS, 0.0f);
            }
            return;
        }
        int required = RebirthConfig.ARMOR_HATCH_RELEASE_SECONDS.get() * 20;
        hatchTicks++;
        entityData.set(HATCH_PROGRESS, required == 0 ? 1.0f : Math.min(1.0f, hatchTicks / (float) required));
        if (hatchTicks >= required) {
            blowHatch(pilot);
        }
    }

    /** Called from the pilot's key: true while the emergency hatch key is held. */
    public void setHatchHeld(Player player, boolean held) {
        if (player == getPilot()) {
            hatchHeld = held;
        }
    }

    private void blowHatch(Player pilot) {
        float cost = (float) (RebirthConfig.ARMOR_HATCH_CRITICAL_COST.get() * getMaxCritical());
        entityData.set(CRITICAL, Math.max(0.0f, getCritical() - cost));
        level().playSound(null, blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.NEUTRAL, 1.0f, 0.6f);
        if (getCritical() <= 0.0f) {
            becomeWreck();
        } else {
            releasePilot();
            pilot.sendSystemMessage(Component.translatable("message.projectrebirth.armor.hatch_blown"));
        }
    }

    // ---------------------------------------------------------------- riding

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && isActive();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return isActive() && getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        setRot(player.getYRot(), 0.0f);
        yRotO = getYRot();
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (pendingJump > 0.0f && onGround()) {
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(motion.x, ArmorUpgradeEffects.jumpStrength(this) * pendingJump, motion.z);
            hasImpulse = true;
        }
        pendingJump = 0.0f;
    }

    // Jumping works like a horse: hold the jump key to charge, release to jump.

    @Override
    public void onPlayerJump(int power) {
        if (isActive()) {
            pendingJump = power >= 90 ? 1.0f : 0.4f + 0.6f * power / 90.0f;
        }
    }

    @Override
    public boolean canJump() {
        return isActive() && stats().jumpStrength().get() > 0;
    }

    @Override
    public void handleStartJump(int power) {
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    public boolean isPickable() {
        if (level().isClientSide && getPassengers().stream().anyMatch(isLocalPlayer)) {
            return false;
        }
        return super.isPickable();
    }

    /**
     * Punch animation, seen by everybody; called on the server when the pilot attacks. One attack can report
     * itself twice (key press and hit), so a punch is not restarted while the previous one is still early.
     */
    public void playAttackAnimation() {
        // The mobile armor's slash is one flowing movement: frequent clicks must not cut it off and jerk the arm back.
        // The worker's jackhammer is a series of blows (1.5.2) and must not start over either.
        int minTicks = getVariant() == ArmorVariant.MOBILE || getVariant() == ArmorVariant.WORKER ? 14 : 8;
        if (tickCount - lastAttackAnimationTick < minTicks && lastAttackAnimationTick != 0) {
            return;
        }
        lastAttackAnimationTick = tickCount;
        triggerAnim(ACTION_CONTROLLER, ATTACK_ANIMATION);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        float strafe = player.xxa * 0.5f;
        float forward = player.zza;
        if (forward <= 0.0f) {
            forward *= 0.5f;
        }
        return new Vec3(strafe, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return pilotSpeed(player);
    }

    /** Speed of the armor under this pilot. Public for tests. */
    public float pilotSpeed(Player player) {
        float speed = (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
        // Mobile armor: faster while its ability slot is selected.
        if (ArmorAbility.selected(player) == ArmorVariant.MOBILE) {
            speed *= 1.0f + RebirthConfig.ARMOR_ABILITY_SPEED_BONUS.get().floatValue();
        }
        speed *= Weapons.aimSpeedMultiplier(player);
        return speed;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        if (getVariant().hasCab()) {
            // the worker's pilot sits on the seat of the glass cab, a little back from the middle (1.5.2)
            return new Vec3(0.0, CAB_SEAT_HEIGHT, -CAB_SEAT_BACK).yRot(-getYRot() * Mth.DEG_TO_RAD);
        }
        // Players ride 0.6 below the attachment point; this puts the pilot's feet above the armor's feet
        // (never inside the floor) and the eyes at the cockpit visor.
        return new Vec3(0.0, 0.9, 0.0);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Direction facing = getDirection();
        // out of an open shell the pilot steps forward, through its open front; otherwise behind the armor
        Direction[] order = opensShell() && !isShellClosed()
                ? new Direction[] {facing, facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()}
                : new Direction[] {facing.getOpposite(), facing.getClockWise(), facing.getCounterClockWise(), facing};
        for (Direction direction : order) {
            for (int up = 0; up <= 1; up++) {
                BlockPos pos = blockPosition().relative(direction, 1).above(up);
                Vec3 safe = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos, true);
                if (safe != null) {
                    return safe;
                }
            }
        }
        // Nowhere around: on top of the armor rather than inside blocks.
        return new Vec3(getX(), getBoundingBox().maxY, getZ());
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide && passenger instanceof Player player) {
            level().playSound(null, blockPosition(), ModSounds.ARMOR_ENTER.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
            setPilotBonuses(player, getVariant());
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof Player player) {
            level().playSound(null, blockPosition(), ModSounds.ARMOR_EXIT.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
            setPilotBonuses(player, null);
            hatchHeld = false;
        }
    }

    /**
     * Pilot bonuses: stronger punches (by variant) with more knockback and longer reach (the armor is big).
     * A null variant removes them.
     */
    public static void setPilotBonuses(Player player, @Nullable ArmorVariant variant) {
        boolean enabled = variant != null;
        setBonus(player, Attributes.ATTACK_DAMAGE, PILOT_ATTACK_MODIFIER, enabled,
                enabled ? RebirthConfig.armorStats(variant).pilotAttackDamageBonus().get() : 0.0);
        setBonus(player, Attributes.ATTACK_KNOCKBACK, PILOT_KNOCKBACK_MODIFIER, enabled, RebirthConfig.ARMOR_PILOT_KNOCKBACK_BONUS.get());
        setBonus(player, Attributes.ENTITY_INTERACTION_RANGE, PILOT_ENTITY_REACH_MODIFIER, enabled,
                RebirthConfig.ARMOR_PILOT_ENTITY_REACH_BONUS.get());
        setBonus(player, Attributes.BLOCK_INTERACTION_RANGE, PILOT_BLOCK_REACH_MODIFIER, enabled,
                RebirthConfig.ARMOR_PILOT_BLOCK_REACH_BONUS.get());
    }

    private static void setBonus(Player player, Holder<Attribute> attribute, ResourceLocation id, boolean enabled, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (enabled && value != 0.0) {
            instance.addPermanentModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || hasPassenger(player)) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(PowerArmorRegistry.REPAIR_WRENCH.get())) {
            useWrench(player, stack);
        } else if (stack.is(PowerArmorRegistry.RESCUE_TOOL.get())) {
            useRescueTool(player, stack);
        } else if (stack.is(PowerArmorRegistry.UPGRADE_MOUNTING_TOOL.get())) {
            useMountingTool(player);
        } else if (isWrecked()) {
            say(player, "wreck");
        } else if (isVehicle()) {
            say(player, "occupied");
        } else if (isDisabled()) {
            say(player, "disabled_empty");
        } else if (!canBeUsedBy(player)) {
            say(player, "not_owner");
        } else if (player.isPassenger()) {
            say(player, "cannot_enter_now");
        } else if (!isShellClosed()) {
            say(player, "shell_busy");
        } else if (opensShell()) {
            openFor(player);
        } else if (player.startRiding(this)) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.armor.entered",
                    Mth.ceil(getStructural()), Mth.ceil(getMaxStructural()), Mth.ceil(getCritical()), Mth.ceil(getMaxCritical())), true);
        }
        return InteractionResult.CONSUME;
    }

    private static void say(Player player, String key) {
        player.displayClientMessage(Component.translatable("message.projectrebirth.armor." + key), true);
    }

    private void useMountingTool(Player player) {
        if (!player.isShiftKeyDown()) {
            say(player, "mounting_hint");
        } else if (isWrecked()) {
            say(player, "wreck");
        } else if (isVehicle()) {
            say(player, "occupied");
        } else if (!canBeUsedBy(player)) {
            say(player, "not_owner");
        } else if (player instanceof ServerPlayer serverPlayer && ArmorMountingMenu.mayMount(player, this)) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, who) -> new ArmorMountingMenu(id, inventory, this),
                    Component.translatable("screen.projectrebirth.armor_mounting.title")), buf -> buf.writeVarInt(getId()));
        }
    }

    private void useWrench(Player player, ItemStack wrench) {
        if (player.getCooldowns().isOnCooldown(wrench.getItem())) {
            return;
        }
        player.getCooldowns().addCooldown(wrench.getItem(), 10);
        if (player.isShiftKeyDown()) {
            if (isWrecked()) {
                dismantle(player);
            } else if (isVehicle()) {
                say(player, "occupied");
            } else if (!canBeUsedBy(player) && !player.isCreative()) {
                say(player, "not_owner");
            } else {
                packUp(player);
            }
            return;
        }
        if (isWrecked()) {
            say(player, "cannot_repair_wreck");
            return;
        }
        // In the field only the outer plating (structural integrity) can be patched up; critical systems need
        // the maintenance station.
        if (getStructural() < getMaxStructural() && consume(player, new ItemStack(Items.IRON_INGOT))) {
            entityData.set(STRUCTURAL, Math.min(getMaxStructural(),
                    getStructural() + RebirthConfig.ARMOR_REPAIR_STRUCTURAL_PER_INGOT.get().floatValue()));
            if (isDisabled() && getStructural() >= RebirthConfig.ARMOR_RESTART_FRACTION.get() * getMaxStructural()) {
                restart();
            }
        } else {
            say(player, getStructural() < getMaxStructural() ? "need_materials"
                    : getCritical() < getMaxCritical() ? "critical_needs_station" : "nothing_to_repair");
            return;
        }
        wrench.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        level().playSound(null, blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.4f);
        player.displayClientMessage(Component.translatable("message.projectrebirth.armor.repaired",
                Mth.ceil(getStructural()), Mth.ceil(getMaxStructural()), Mth.ceil(getCritical()), Mth.ceil(getMaxCritical())), true);
    }

    /** Takes one matching item from the player (free in creative). */
    private static boolean consume(Player player, ItemStack wanted) {
        if (player.isCreative()) {
            return true;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(wanted.getItem())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private void useRescueTool(Player player, ItemStack tool) {
        Player pilot = getPilot();
        if (!isDisabled() || pilot == null) {
            say(player, "rescue_not_needed");
            return;
        }
        releasePilot();
        tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        level().playSound(null, blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 0.8f);
        say(player, "rescued");
        pilot.sendSystemMessage(Component.translatable("message.projectrebirth.armor.rescued_by", player.getDisplayName()));
    }

    private void packUp(Player player) {
        if (!isAlive()) {
            return;
        }
        ItemStack stack = new ItemStack(PowerArmorRegistry.item(getVariant()));
        if (getStructural() < getMaxStructural()) {
            stack.set(PowerArmorRegistry.STORED_STRUCTURAL.get(), getStructural());
        }
        if (getCritical() < getMaxCritical()) {
            stack.set(PowerArmorRegistry.STORED_CRITICAL.get(), getCritical());
        }
        // The maintenance station repairs up to the maximum with plates. Kept as a bonus, so that config changes count.
        float structuralBonus = ArmorUpgradeEffects.structuralBonus(registryAccess(), upgrades());
        float criticalBonus = ArmorUpgradeEffects.criticalBonus(registryAccess(), upgrades());
        if (structuralBonus > 0.0f) {
            stack.set(PowerArmorRegistry.PLATING_STRUCTURAL.get(), structuralBonus);
        }
        if (criticalBonus > 0.0f) {
            stack.set(PowerArmorRegistry.PLATING_CRITICAL.get(), criticalBonus);
        }
        Upgrades.putOnStack(stack, upgrades());
        entityData.set(UPGRADES, InstalledUpgrades.EMPTY);
        discard();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private void dismantle(Player player) {
        if (!isAlive()) {
            return;
        }
        discard();
        Upgrades.takeAll(this).forEach(this::spawnAtLocation);
        spawnAtLocation(new ItemStack(Items.IRON_INGOT, RebirthConfig.ARMOR_DISMANTLE_IRON.get()));
        spawnAtLocation(new ItemStack(PowerArmorRegistry.ARMOR_PLATE.get(), RebirthConfig.ARMOR_DISMANTLE_PLATES.get()));
        level().playSound(null, blockPosition(), SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, 0.8f, 1.0f);
        say(player, "dismantled");
    }

    // ---------------------------------------------------------------- misc

    /** Heavy metal steps instead of the block's own step sound. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.ARMOR_STEP.get(), 1.0f, 1.0f);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(PowerArmorRegistry.item(getVariant()));
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // An armor without a pilot is parked: a solid object that only falls (see Parked). With a pilot it moves as before.

    private boolean isParked() {
        return !isVehicle();
    }

    @Override
    public boolean canBeCollidedWith() {
        return isAlive() && isParked();
    }

    @Override
    public boolean isPushedByFluid() {
        return !isParked() && super.isPushedByFluid();
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!isParked()) {
            super.knockback(strength, x, z);
        }
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return !isParked() && super.canRide(vehicle);
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (!isParked() || Parked.allowsMove(type)) {
            super.move(type, movement);
        }
    }

    @Override
    public void travel(Vec3 input) {
        if (isParked() || !isShellClosed()) {
            Parked.keepOnlyFalling(this);
            super.travel(Vec3.ZERO);
        } else {
            super.travel(input);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Structural", getStructural());
        tag.putFloat("Critical", getCritical());
        tag.putByte("ArmorState", entityData.get(STATE));
        tag.putBoolean("Initialized", initialized);
        tag.putString("Variant", getVariant().id());
        tag.putBoolean("WideMining", isWideMining());
        Upgrades.save(tag, registryAccess(), upgrades());
        tag.putBoolean("FloodlightOn", isFloodlightOn());
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(STRUCTURAL, tag.getFloat("Structural"));
        entityData.set(CRITICAL, tag.getFloat("Critical"));
        entityData.set(STATE, tag.getByte("ArmorState"));
        initialized = tag.getBoolean("Initialized");
        // Armors from 0.1.0 have no variant: they are combat armors.
        setVariant(ArmorVariant.byId(tag.getString("Variant")));
        setWideMining(!tag.contains("WideMining") || tag.getBoolean("WideMining"));
        entityData.set(UPGRADES, Upgrades.load(tag, registryAccess()));
        setFloodlightOn(tag.getBoolean("FloodlightOn"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (isWrecked()) {
                return state.setAndContinue(WRECKED);
            }
            if (isDisabled()) {
                return state.setAndContinue(SHUTDOWN);
            }
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        // The shell: opens and holds open, then closes; shut, it does nothing (the parts rest closed).
        controllers.add(new AnimationController<>(this, "shell", 0, state -> switch (shellState()) {
            case SHELL_OPENING, SHELL_OPEN -> state.setAndContinue(SHELL_OPEN_ANIMATION);
            case SHELL_CLOSING -> state.setAndContinue(SHELL_CLOSE_ANIMATION);
            default -> PlayState.STOP;
        }));
        // The worker's jackhammer while its pilot breaks a block (1.5.2); over the walk and the stance.
        controllers.add(new AnimationController<>(this, "work", 3,
                state -> isMining() && isActive() && getVariant() == ArmorVariant.WORKER ? state.setAndContinue(MINE) : PlayState.STOP));
        // Plays only when triggered; its arm and torso keyframes override the main animation for that moment.
        controllers.add(new AnimationController<>(this, ACTION_CONTROLLER, 2, state -> PlayState.STOP)
                .triggerableAnim(ATTACK_ANIMATION, ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
