package com.projectrebirth.environment;

import java.util.Locale;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.technology.energy.EnergyUtil;
import com.projectrebirth.technology.energy.ModEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Works while it has energy and the place around it is colder than its thermostat (without heaters): then everything within
 * its radius is warmer (see {@link Temperature}). It does not cool, and it does not heat a place that is warm enough already.
 * Not life support: the cable feeds it like any machine.
 */
public class HeaterBlockEntity extends BlockEntity {
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.HEATER_CAPACITY.get(),
            () -> Math.max(1, RebirthConfig.HEATER_ENERGY.get() * 10), () -> 0, this::setChanged);
    private boolean working;
    private float around;

    public HeaterBlockEntity(BlockPos pos, BlockState state) {
        super(TemperatureRegistry.HEATER_ENTITY.get(), pos, state);
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    public boolean working() {
        return working;
    }

    private static int energyPerTick() {
        return RebirthConfig.STATIONS_NEED_ENERGY.get() ? RebirthConfig.HEATER_ENERGY.get() : 0;
    }

    void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (server.getGameTime() % 20 == 0) {
            around = Temperature.withoutHeaters(server, worldPosition.above());
            boolean wanted = TemperatureRegistry.enabled() && around < RebirthConfig.HEATER_THERMOSTAT.get()
                    && energy.getEnergyStored() >= energyPerTick();
            if (wanted != working) {
                working = wanted;
                Temperature.heaterWorking(server, worldPosition, working);
                TechSounds.setWorking(this, working);
            }
        }
        if (working && !energy.consume(energyPerTick())) {
            working = false;
            Temperature.heaterWorking(server, worldPosition, false);
            TechSounds.setWorking(this, false);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            Temperature.heaterWorking(level, worldPosition, false);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            Temperature.heaterWorking(level, worldPosition, false);
        }
    }

    /** The line shown on right-click. */
    public Component status() {
        return Component.translatable("message.projectrebirth.heater.status",
                Component.translatable(working ? "message.projectrebirth.heater.on" : "message.projectrebirth.heater.off"),
                String.format(Locale.ROOT, "%.0f", around), String.format(Locale.ROOT, "%.0f", RebirthConfig.HEATER_THERMOSTAT.get()),
                EnergyUtil.format(energy.getEnergyStored()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.saved());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.load(tag.getInt("energy"));
    }
}
