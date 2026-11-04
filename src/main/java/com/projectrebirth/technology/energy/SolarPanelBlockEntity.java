package com.projectrebirth.technology.energy;

import com.projectrebirth.technology.TechSounds;
import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.TechBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Makes energy in daylight under the open sky, not in rain, and gives it to the blocks below and around it. */
public class SolarPanelBlockEntity extends BlockEntity {
    private static final Direction[] OUTPUT_SIDES = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.SOLAR_CAPACITY.get(),
            () -> 0, () -> RebirthConfig.GENERATOR_OUTPUT.get(), this::setChanged);
    /** Checked once a second, not every tick. */
    private boolean producing;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(TechBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    public boolean producing() {
        return producing;
    }

    /** Daylight, full sky light right above the panel (glass and leaves let it through) and no rain. Public for tests. */
    public boolean seesSun() {
        BlockPos above = worldPosition.above();
        return level.isDay() && level.canSeeSky(above) && !level.isRainingAt(above);
    }

    void serverTick() {
        if (level.getGameTime() % 20 == 0) {
            producing = seesSun();
            TechSounds.setWorking(this, producing);
        }
        if (producing) {
            energy.generate(RebirthConfig.SOLAR_ENERGY_PER_TICK.get());
        }
        EnergyUtil.push(level, worldPosition, energy, RebirthConfig.GENERATOR_OUTPUT.get(), OUTPUT_SIDES);
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
