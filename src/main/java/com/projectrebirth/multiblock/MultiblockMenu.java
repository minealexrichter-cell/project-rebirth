package com.projectrebirth.multiblock;

import org.jetbrains.annotations.Nullable;

import com.projectrebirth.technology.energy.EnergyUtil;

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

/** A controller's window: the item to start from, nine material slots, the result, the recipe arrows, energy and the state. */
public class MultiblockMenu extends AbstractContainerMenu {
    /** 1.8.0: the window of style A (440 x 250); materials 3 x 3 on the right, the base and the result under them. */
    public static final int MATERIALS_X = 352;
    public static final int MATERIALS_Y = 37;
    public static final int MATERIAL_STEP = 20;
    public static final int BASE_X = 352;
    public static final int BASE_Y = 110;
    public static final int OUTPUT_X = 398;
    public static final int OUTPUT_Y = 113;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 167;

    private final BlockPos pos;
    private final ContainerData data;
    @Nullable
    private final MultiblockControllerBlockEntity controller;

    public MultiblockMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), new ItemStackHandler(MultiblockControllerBlockEntity.SLOTS),
                new SimpleContainerData(MultiblockControllerBlockEntity.DATA_COUNT), null);
    }

    MultiblockMenu(int containerId, Inventory inventory, BlockPos pos, IItemHandler items, ContainerData data,
                   @Nullable MultiblockControllerBlockEntity controller) {
        super(MultiblockRegistry.CONTROLLER_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        this.controller = controller;
        addSlot(new SlotItemHandler(items, MultiblockControllerBlockEntity.BASE_SLOT, BASE_X, BASE_Y));
        for (int i = 0; i < MultiblockControllerBlockEntity.MATERIALS; i++) {
            addSlot(new SlotItemHandler(items, MultiblockControllerBlockEntity.FIRST_MATERIAL + i, MATERIALS_X + (i % 3) * MATERIAL_STEP, MATERIALS_Y + (i / 3) * MATERIAL_STEP));
        }
        addSlot(new SlotItemHandler(items, MultiblockControllerBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    public int energy() {
        return EnergyUtil.join(data.get(MultiblockControllerBlockEntity.DATA_ENERGY_LOW), data.get(MultiblockControllerBlockEntity.DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(MultiblockControllerBlockEntity.DATA_CAPACITY_LOW), data.get(MultiblockControllerBlockEntity.DATA_CAPACITY_HIGH));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (controller == null) {
            return false;
        }
        controller.cycleRecipe(id == MultiblockControllerBlockEntity.BUTTON_PREVIOUS ? -1 : 1);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof MultiblockControllerBlock
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
        int own = MultiblockControllerBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, MultiblockControllerBlockEntity.FIRST_MATERIAL, MultiblockControllerBlockEntity.OUTPUT_SLOT, false)) {
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
