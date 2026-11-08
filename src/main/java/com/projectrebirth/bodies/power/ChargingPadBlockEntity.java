package com.projectrebirth.bodies.power;

import com.projectrebirth.bodies.ArtificialBodyEntity;
import com.projectrebirth.bodies.TransferService;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.ModEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Charges what stands on it from its energy buffer, which the cable fills: every body (empty or controlled) gets up to
 * padChargePerTick, then the body battery put into the pad. Not a life support block: the cable feeds it like any machine.
 * With energy.stationsNeedEnergy = false it charges for free, like the stations work for free.
 */
public class ChargingPadBlockEntity extends BlockEntity {
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.CHARGING_PAD_CAPACITY.get(),
            () -> RebirthConfig.CHARGING_PAD_RECEIVE.get(), () -> 0, this::setChanged);
    private ItemStack battery = ItemStack.EMPTY;

    public ChargingPadBlockEntity(BlockPos pos, BlockState state) {
        super(BodyPowerRegistry.CHARGING_PAD_ENTITY.get(), pos, state);
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    public ItemStack battery() {
        return battery;
    }

    public void setBattery(ItemStack battery) {
        this.battery = battery;
        setChanged();
    }

    private AABB top() {
        return new AABB(worldPosition.above()).expandTowards(0, 1, 0).deflate(0.05, 0, 0.05).move(0, -0.8, 0);
    }

    /** What may be given now, at most {@code wanted}: all of it when energy is free. */
    private int available(int wanted) {
        return RebirthConfig.STATIONS_NEED_ENERGY.get() ? Math.min(wanted, energy.getEnergyStored()) : wanted;
    }

    private void use(int amount) {
        if (RebirthConfig.STATIONS_NEED_ENERGY.get()) {
            energy.take(amount, false);
        }
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server) || !BodyPower.enabled()) {
            TechSounds.setWorking(this, false);
            return;
        }
        int rate = RebirthConfig.CHARGING_PAD_RATE.get();
        boolean gave = false;
        for (LivingEntity entity : server.getEntitiesOfClass(LivingEntity.class, top(), LivingEntity::isAlive)) {
            if (entity instanceof ArtificialBodyEntity body) {
                int amount = available((int) Math.min(rate, BodyPower.capacity(body) - body.charge()));
                if (amount > 0) {
                    body.setCharge(body.charge() + amount);
                    use(amount);
                    gave = true;
                }
            } else if (entity instanceof ServerPlayer player && TransferService.isInArtificialBody(player)) {
                int amount = available((int) Math.min(rate, BodyPower.capacity(player) - BodyPower.charge(player)));
                if (amount > 0) {
                    BodyPower.addCharge(player, amount);
                    use(amount);
                    gave = true;
                }
            }
        }
        if (!battery.isEmpty()) {
            int amount = available(Math.min(rate, BodyBatteryItem.capacity() - BodyBatteryItem.stored(battery)));
            if (amount > 0) {
                BodyBatteryItem.setStored(battery, BodyBatteryItem.stored(battery) + amount);
                use(amount);
                setChanged();
                gave = true;
            }
        }
        if (gave && server.getGameTime() % 10 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + 0.3,
                    worldPosition.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.02);
        }
        TechSounds.setWorking(this, gave);
    }

    /** The line shown on right-click with an empty hand. */
    public Component status() {
        return Component.translatable("message.projectrebirth.charging_pad.status", EnergyUtil.format(energy.getEnergyStored()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.saved());
        if (!battery.isEmpty()) {
            tag.put("battery", battery.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.load(tag.getInt("energy"));
        battery = tag.contains("battery") ? ItemStack.parse(registries, tag.getCompound("battery")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
    }
}
