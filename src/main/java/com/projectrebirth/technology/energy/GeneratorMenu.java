package com.projectrebirth.technology.energy;

import com.projectrebirth.technology.TechMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Combustion generator window: the fuel slot, burn time, stored energy and the player's inventory. */
public class GeneratorMenu extends AbstractContainerMenu {
    public static final int FUEL_X = 26;
    public static final int FUEL_Y = 34;
    public static final int INVENTORY_Y = 92;

    private final BlockPos pos;
    private final ContainerData data;

    /** Client side: the contents come from the server through slot and data syncing. */
    public GeneratorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), new ItemStackHandler(1),
                new SimpleContainerData(CombustionGeneratorBlockEntity.DATA_COUNT));
    }

    GeneratorMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data) {
        super(TechMenus.GENERATOR.get(), containerId);
        this.pos = pos;
        this.data = data;
        addSlot(new SlotItemHandler(items, CombustionGeneratorBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CombustionGeneratorBlockEntity.burnTime(stack) > 0;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    /** Remaining burn time of the current fuel, 0..1. */
    public float burn() {
        int total = data.get(CombustionGeneratorBlockEntity.DATA_BURN_TOTAL);
        return total <= 0 ? 0.0f : Math.min(1.0f, data.get(CombustionGeneratorBlockEntity.DATA_BURN) / (float) total);
    }

    public boolean burning() {
        return data.get(CombustionGeneratorBlockEntity.DATA_BURN) > 0;
    }

    public int energy() {
        return EnergyUtil.join(data.get(CombustionGeneratorBlockEntity.DATA_ENERGY_LOW), data.get(CombustionGeneratorBlockEntity.DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(CombustionGeneratorBlockEntity.DATA_CAPACITY_LOW), data.get(CombustionGeneratorBlockEntity.DATA_CAPACITY_HIGH));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof CombustionGeneratorBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (CombustionGeneratorBlockEntity.burnTime(stack) <= 0 || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return original;
    }
}
