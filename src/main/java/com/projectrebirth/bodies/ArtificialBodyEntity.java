package com.projectrebirth.bodies;

import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.core.ModSounds;
import com.projectrebirth.core.Parked;
import com.projectrebirth.upgrades.InstalledUpgrades;
import com.projectrebirth.upgrades.UpgradeHost;
import com.projectrebirth.upgrades.UpgradeLayout;
import com.projectrebirth.upgrades.Upgrades;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * An unoccupied artificial body standing in the world. Shared by all body classes;
 * per-class stats come from {@link RebirthConfig}. While a player controls the body, this entity
 * does not exist: the player is the body (see {@link TransferService}).
 */
public class ArtificialBodyEntity extends PathfinderMob implements GeoEntity, UpgradeHost {
    public static final String MAIN_CONTROLLER = "main";
    public static final String INTERACT_ANIMATION = "interact";

    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.body.idle");
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.body.walk");
    public static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.body.run");
    public static final RawAnimation SNEAK = RawAnimation.begin().thenLoop("animation.body.sneak");
    public static final RawAnimation CROUCH = RawAnimation.begin().thenLoop("animation.body.crouch");
    private static final RawAnimation INTERACT = RawAnimation.begin().thenPlay("animation.body.interact");

    private final BodyClass bodyClass;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID owner;
    private ListTag storedInventory = new ListTag();
    private InstalledUpgrades upgrades = InstalledUpgrades.EMPTY;
    /** False only for a body that has never been in a level; saved so loaded bodies keep their damage. */
    private boolean statsInitialized;

    public ArtificialBodyEntity(EntityType<? extends ArtificialBodyEntity> type, Level level, BodyClass bodyClass) {
        super(type, level);
        this.bodyClass = bodyClass;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    public BodyClass getBodyClass() {
        return bodyClass;
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    /** A body without an owner (e.g. summoned by command) can be used by anyone and becomes theirs. */
    public boolean canBeUsedBy(Player player) {
        return owner == null || owner.equals(player.getUUID());
    }

    /** The inventory a player gets while controlling this body, in vanilla inventory NBT format. */
    public ListTag getStoredInventory() {
        return storedInventory;
    }

    public void setStoredInventory(ListTag inventory) {
        this.storedInventory = inventory;
    }

    @Override
    public InstalledUpgrades upgrades() {
        return upgrades;
    }

    @Override
    public void setUpgrades(InstalledUpgrades upgrades) {
        this.upgrades = upgrades;
        if (!level().isClientSide && statsInitialized) {
            applyArmor();
        }
    }

    @Override
    public UpgradeLayout layout() {
        return new UpgradeLayout.Counted(RebirthConfig.bodyStats(bodyClass).upgradeSlots().get());
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide) {
            applyClassStats();
            BodyRegistry.get(getServer()).updateStanding(this);
        }
    }

    /** Re-applied on every load so that config changes reach existing bodies. A brand-new body starts at full integrity. */
    private void applyClassStats() {
        RebirthConfig.BodyStats stats = RebirthConfig.bodyStats(bodyClass);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(stats.maxHealth().get());
        applyArmor();
        if (!statsInitialized || getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
        statsInitialized = true;
    }

    private void applyArmor() {
        double armor = RebirthConfig.bodyStats(bodyClass).armor().get();
        getAttribute(Attributes.ARMOR).setBaseValue(armor + BodyGadgets.armorBonus(registryAccess(), upgrades));
        getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(BodyGadgets.toughnessBonus(registryAccess(), upgrades));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            packUp(player);
        } else {
            player.displayClientMessage(Component.translatable("message.projectrebirth.body.status",
                    getDisplayName(), Mth.ceil(getHealth()), Mth.ceil(getMaxHealth())), true);
            triggerAnim(MAIN_CONTROLLER, INTERACT_ANIMATION);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Turns the body back into an item; its inventory drops on the ground.
     * The entity is removed first, so it can never be packed twice.
     */
    private void packUp(Player player) {
        if (!isAlive()) {
            return;
        }
        if (!canBeUsedBy(player) && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.body.not_owner"), true);
            return;
        }
        ItemStack stack = new ItemStack(BodyItems.item(bodyClass));
        if (getHealth() < getMaxHealth()) {
            stack.set(BodyComponents.STORED_INTEGRITY.get(), getHealth());
        }
        if (hasCustomName()) {
            stack.set(DataComponents.CUSTOM_NAME, getCustomName());
        }
        Upgrades.putOnStack(stack, upgrades);
        upgrades = InstalledUpgrades.EMPTY;
        if (owner != null) {
            BodyRegistry.get(getServer()).remove(owner, getUUID());
        }
        InventoryUtil.dropAll(this, storedInventory);
        storedInventory = new ListTag();
        discard();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && owner != null) {
            BodyRegistry.get(getServer()).markDestroyed(owner, getUUID(), level().dimension(), position());
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        InventoryUtil.dropAll(this, storedInventory);
        storedInventory = new ListTag();
        Upgrades.takeAll(this).forEach(this::spawnAtLocation);
    }

    // Robot bodies sound like machines; synthetic bodies keep the ordinary sounds.

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return bodyClass.synthetic() ? super.getHurtSound(source) : ModSounds.ROBOT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return bodyClass.synthetic() ? super.getDeathSound() : ModSounds.ROBOT_DESTROYED.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(BodyItems.item(bodyClass));
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

    // Nobody controls a body standing in the world (a controlled body is the player), so it is always parked.

    @Override
    public boolean canBeCollidedWith() {
        return isAlive();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (Parked.allowsMove(type)) {
            super.move(type, movement);
        }
    }

    @Override
    public void travel(Vec3 input) {
        Parked.keepOnlyFalling(this);
        super.travel(Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        Parked.holdRotation(this);
        if (!level().isClientSide && tickCount % 20 == 0) {
            BodyRules.tickSelfRepair(this, bodyClass);
            BodyRegistry.get(getServer()).updateStanding(this);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.put("BodyInventory", storedInventory);
        tag.putBoolean("StatsInitialized", statsInitialized);
        Upgrades.save(tag, registryAccess(), upgrades);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        storedInventory = tag.getList("BodyInventory", Tag.TAG_COMPOUND);
        upgrades = Upgrades.load(tag, registryAccess());
        // Bodies saved before this flag existed are already initialized.
        // Saves from before the flag existed already have health; a body summoned by command with extra data
        // (for example /summon ... {Owner:...}) has none and still needs its class health.
        statsInitialized = tag.contains("StatsInitialized") ? tag.getBoolean("StatsInitialized") : tag.contains("Health");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, MAIN_CONTROLLER, 5, state -> state.setAndContinue(IDLE))
                .triggerableAnim(INTERACT_ANIMATION, INTERACT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
