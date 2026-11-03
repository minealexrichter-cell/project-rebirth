package com.projectrebirth.technology.energy;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.AutomationHandler;
import com.projectrebirth.technology.TechBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Burns anything a furnace burns, for as long as a furnace would, and makes energy meanwhile. It stops burning
 * while its buffer is full, so no fuel is wasted, and gives energy to the blocks around it.
 */
public class CombustionGeneratorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FUEL_SLOT = 0;
    public static final int DATA_BURN = 0;
    public static final int DATA_BURN_TOTAL = 1;
    public static final int DATA_ENERGY_LOW = 2;
    public static final int DATA_ENERGY_HIGH = 3;
    public static final int DATA_CAPACITY_LOW = 4;
    public static final int DATA_CAPACITY_HIGH = 5;
    public static final int DATA_COUNT = 6;

    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /** Hoppers put fuel in and take out what is left of it (an empty bucket after lava). */
    private final AutomationHandler automation = new AutomationHandler(items,
            (slot, stack) -> burnTime(stack) > 0, slot -> burnTime(items.getStackInSlot(slot)) == 0);
    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.COMBUSTION_CAPACITY.get(),
            () -> 0, () -> RebirthConfig.GENERATOR_OUTPUT.get(), this::setChanged);
    private int burnTime;
    private int burnTotal;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_BURN -> burnTime;
                case DATA_BURN_TOTAL -> burnTotal;
                case DATA_ENERGY_LOW -> EnergyUtil.low(energy.getEnergyStored());
                case DATA_ENERGY_HIGH -> EnergyUtil.high(energy.getEnergyStored());
                case DATA_CAPACITY_LOW -> EnergyUtil.low(energy.getMaxEnergyStored());
                case DATA_CAPACITY_HIGH -> EnergyUtil.high(energy.getMaxEnergyStored());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public CombustionGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(TechBlockEntities.COMBUSTION_GENERATOR.get(), pos, state);
    }

    public static int burnTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
    }

    public ItemStackHandler items() {
        return items;
    }

    public AutomationHandler automation() {
        return automation;
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    void serverTick() {
        if (!energy.isFull()) {
            if (burnTime <= 0) {
                startBurning();
            }
            if (burnTime > 0) {
                burnTime--;
                energy.generate(RebirthConfig.COMBUSTION_ENERGY_PER_TICK.get());
            }
        }
        EnergyUtil.push(level, worldPosition, energy, RebirthConfig.GENERATOR_OUTPUT.get(), Direction.values());
        boolean lit = burnTime > 0;
        if (getBlockState().getValue(CombustionGeneratorBlock.LIT) != lit) {
            level.setBlock(worldPosition, getBlockState().setValue(CombustionGeneratorBlock.LIT, lit), 3);
        }
    }

    private void startBurning() {
        ItemStack fuel = items.getStackInSlot(FUEL_SLOT);
        int time = burnTime(fuel);
        if (time <= 0) {
            return;
        }
        burnTime = time;
        burnTotal = time;
        ItemStack remainder = fuel.getCraftingRemainingItem();
        ItemStack left = fuel.copy();
        left.shrink(1);
        items.setStackInSlot(FUEL_SLOT, left.isEmpty() ? remainder : left);
    }

    void dropContents() {
        ItemStack stack = items.getStackInSlot(FUEL_SLOT);
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        items.setStackInSlot(FUEL_SLOT, ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("burn", burnTime);
        tag.putInt("burnTotal", burnTotal);
        tag.putInt("energy", energy.saved());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        burnTime = tag.getInt("burn");
        burnTotal = tag.getInt("burnTotal");
        energy.load(tag.getInt("energy"));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GeneratorMenu(containerId, inventory, worldPosition, items, data);
    }
}
