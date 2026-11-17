package com.projectrebirth.garrison;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A packed turret just built in the heavy dock (1.7.1): a crate that rolls out through the gate, as power armor does. Right-click
 * (or a hit) by whoever may manage the turret gives its item, which is set on a turret mount as before. It holds the item whole:
 * its owner, upgrades and everything else go with it.
 */
public class TurretCrateEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(TurretCrateEntity.class, EntityDataSerializers.ITEM_STACK);

    @Nullable
    private UUID owner;
    @Nullable
    private Vec3 rolloutTarget;
    private int rolloutStuck;

    public TurretCrateEntity(EntityType<? extends TurretCrateEntity> type, Level level) {
        super(type, level);
    }

    /** Sets a crate with this turret up in the dock and starts it rolling (server); null if there is no room. */
    @Nullable
    public static TurretCrateEntity rollOut(ServerLevel level, ItemStack turret, Vec3 at, Vec3 exit, float yaw, @Nullable UUID dockOwner) {
        TurretCrateEntity crate = GarrisonRegistry.TURRET_CRATE.get().create(level);
        if (crate == null) {
            return null;
        }
        crate.setItem(turret.copy());
        crate.owner = PackedMachineItem.packed(turret).owner().orElse(dockOwner);
        crate.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        if (!level.noCollision(crate)) {
            return null;
        }
        level.addFreshEntity(crate);
        crate.rolloutTarget = exit;
        level.playSound(null, crate.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.8f, 0.6f);
        return crate;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
    }

    public ItemStack getItem() {
        return entityData.get(ITEM);
    }

    public void setItem(ItemStack stack) {
        entityData.set(ITEM, stack);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public boolean isRollingOut() {
        return rolloutTarget != null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (getItem().isEmpty()) {
            discard();
            return;
        }
        Vec3 step = Vec3.ZERO;
        if (rolloutTarget != null) {
            Vec3 left = new Vec3(rolloutTarget.x - getX(), 0.0, rolloutTarget.z - getZ());
            if (left.length() < 0.03 || rolloutStuck > 40) {
                rolloutTarget = null;
                level().playSound(null, blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 0.7f);
            } else {
                double speed = com.projectrebirth.config.RebirthConfig.DOCK_ROLLOUT_SPEED.get();
                step = left.length() > speed ? left.normalize().scale(speed) : left;
            }
        }
        double fall = onGround() ? 0.0 : Math.max(getDeltaMovement().y - 0.04, -0.6);
        setDeltaMovement(0.0, fall, 0.0);
        Vec3 before = position();
        move(MoverType.SELF, step.add(0.0, fall, 0.0));
        if (rolloutTarget != null) {
            rolloutStuck = position().subtract(before).horizontalDistanceSqr() < 1.0e-6 ? rolloutStuck + 1 : 0;
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        take(player);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof Player player) {
            take(player);
            return true;
        }
        return false;
    }

    /** Gives the turret to the player if they may manage it; the crate is gone. */
    private void take(Player player) {
        if (isRemoved() || getItem().isEmpty()) {
            return;
        }
        if (!MachineCore.mayCommand(owner, player)) {
            player.displayClientMessage(Component.translatable("message.projectrebirth.machine.not_yours"), true);
            return;
        }
        ItemStack stack = getItem().copy();
        setItem(ItemStack.EMPTY);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        level().playSound(null, blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8f, 0.9f);
        discard();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return getItem().copy();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setItem(ItemStack.parseOptional(registryAccess(), tag.getCompound("Item")));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!getItem().isEmpty()) {
            tag.put("Item", getItem().save(registryAccess()));
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    public Component getName() {
        return getItem().isEmpty() ? super.getName() : Component.translatable("entity.projectrebirth.turret_crate.of", getItem().getHoverName());
    }
}
