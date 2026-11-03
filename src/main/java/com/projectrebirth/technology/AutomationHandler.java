package com.projectrebirth.technology;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * What hoppers, pipes and other machines see of a station: the same slots, but only the allowed ones accept or give
 * items. Players use the window and are not limited by this.
 */
public class AutomationHandler implements IItemHandler {
    private final ItemStackHandler items;
    private final BiPredicate<Integer, ItemStack> canInsert;
    private final IntPredicate canExtract;

    public AutomationHandler(ItemStackHandler items, BiPredicate<Integer, ItemStack> canInsert, IntPredicate canExtract) {
        this.items = items;
        this.canInsert = canInsert;
        this.canExtract = canExtract;
    }

    @Override
    public int getSlots() {
        return items.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return items.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return canInsert.test(slot, stack) ? items.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return canExtract.test(slot) ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return items.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return canInsert.test(slot, stack);
    }
}
