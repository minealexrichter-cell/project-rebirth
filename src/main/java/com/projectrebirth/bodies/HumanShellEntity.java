package com.projectrebirth.bodies;

import java.util.Optional;
import java.util.UUID;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.environment.HumanBody;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * The player's own body standing in a transfer capsule while their consciousness controls an artificial body.
 * Its inventory lives in {@link ShellRegistry}; this entity only shows the body and can be hurt or killed.
 */
public class HumanShellEntity extends Mob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(HumanShellEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private UUID shellId;

    public HumanShellEntity(EntityType<? extends HumanShellEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
    }

    public void setup(UUID owner, UUID shellId, Component ownerName) {
        entityData.set(OWNER, Optional.of(owner));
        this.shellId = shellId;
        setCustomName(ownerName);
    }

    public Optional<UUID> getOwnerId() {
        return entityData.get(OWNER);
    }

    public UUID getShellId() {
        return shellId;
    }

    @Override
    public void tick() {
        super.tick();
        // Once a second: a shell whose record is gone (owner returned, or a stale copy) must disappear.
        if (level() instanceof ServerLevel serverLevel && tickCount % 20 == 1) {
            if (!isRecorded(serverLevel)) {
                discard();
            } else if (serverLevel.getServer().getPlayerList().getPlayer(getOwnerId().get()) instanceof ServerPlayer owner) {
                HumanBody.tickShell(this, serverLevel, owner);
            }
        }
    }

    private boolean isRecorded(ServerLevel level) {
        Optional<UUID> owner = getOwnerId();
        return owner.isPresent() && shellId != null
                && ShellRegistry.get(level.getServer()).isCurrentShell(owner.get(), shellId);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!RebirthConfig.HUMAN_BODY_VULNERABLE.get() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && level() instanceof ServerLevel serverLevel && isRecorded(serverLevel) && isAlive()) {
            ShellRegistry.get(serverLevel.getServer()).updateHealth(getOwnerId().get(), shellId, getHealth());
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel serverLevel && isRecorded(serverLevel)) {
            UUID owner = getOwnerId().get();
            ShellRegistry.get(serverLevel.getServer()).remove(owner)
                    .ifPresent(record -> InventoryUtil.dropAll(this, record.inventory()));
            ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) {
                ownerPlayer.sendSystemMessage(Component.translatable("message.projectrebirth.transfer.human_body_died"));
            }
        }
        super.die(source);
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getOwnerId().ifPresent(owner -> tag.putUUID("Owner", owner));
        if (shellId != null) {
            tag.putUUID("ShellId", shellId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        }
        if (tag.hasUUID("ShellId")) {
            shellId = tag.getUUID("ShellId");
        }
    }
}
