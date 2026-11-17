package com.projectrebirth.garrison;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.technology.energy.ModEnergyStorage;
import com.projectrebirth.weapons.WeaponRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The turret mount (1.7.0, block G): the block a defence turret stands on. Energy comes in from cables, ammunition from hoppers
 * (or a player's hand): assault rifle rounds and rockets, nothing else. The turret on it takes both shot by shot.
 */
public class TurretMountBlockEntity extends BlockEntity {
    public static final int SLOTS = 9;

    private final ModEnergyStorage energy = new ModEnergyStorage(() -> RebirthConfig.TURRET_MOUNT_CAPACITY.get(),
            () -> Math.max(1, RebirthConfig.TURRET_MOUNT_CAPACITY.get() / 20), () -> 0, this::setChanged);
    private final ItemStackHandler ammo = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isAmmo(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public TurretMountBlockEntity(BlockPos pos, BlockState state) {
        super(GarrisonRegistry.TURRET_MOUNT_ENTITY.get(), pos, state);
    }

    public static boolean isAmmo(ItemStack stack) {
        return stack.is(WeaponRegistry.ASSAULT_RIFLE_AMMO.get()) || stack.is(GarrisonRegistry.ROCKET.get());
    }

    public ModEnergyStorage energy() {
        return energy;
    }

    public ItemStackHandler ammo() {
        return ammo;
    }

    /** How many of this ammunition it holds. */
    public int count(Item item) {
        int total = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (ammo.getStackInSlot(i).is(item)) {
                total += ammo.getStackInSlot(i).getCount();
            }
        }
        return total;
    }

    /**
     * One shot: takes the energy and one piece of the ammunition, or nothing when either is missing. Public for tests.
     */
    public boolean takeShot(Item item, int energyCost) {
        if (energy.getEnergyStored() < energyCost) {
            return false;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (ammo.getStackInSlot(i).is(item)) {
                ammo.extractItem(i, 1, false);
                energy.consume(energyCost);
                return true;
            }
        }
        return false;
    }

    /** Whether a shot could be taken now (energy and ammunition). */
    public boolean ready(Item item, int energyCost) {
        return energy.getEnergyStored() >= energyCost && count(item) > 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy.saved());
        tag.put("ammo", ammo.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.load(tag.getInt("energy"));
        if (tag.contains("ammo")) {
            ammo.deserializeNBT(registries, tag.getCompound("ammo"));
        }
    }
}
