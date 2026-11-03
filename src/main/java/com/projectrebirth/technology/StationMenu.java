package com.projectrebirth.technology;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Station window: recipe list (picked with a menu button click, like the stonecutter), six input slots, one output
 * slot and the player's inventory. Slot positions are shared with the screen.
 */
public class StationMenu extends AbstractContainerMenu {
    public static final int INPUT_X = 26;
    public static final int INPUT_Y = 68;
    public static final int OUTPUT_X = 124;
    public static final int OUTPUT_Y = 77;
    public static final int INVENTORY_Y = 122;

    private final Station station;
    private final BlockPos pos;
    private final ContainerData data;
    private final List<RecipeHolder<FabricationRecipe>> recipes;
    private final StationBlockEntity blockEntity;

    /** Client side: the contents come from the server through slot and data syncing. */
    public StationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    private StationMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, pos, stationAt(inventory.player, pos), null,
                new ItemStackHandler(StationBlockEntity.INPUT_SLOTS + 1), new SimpleContainerData(3));
    }

    StationMenu(int containerId, Inventory inventory, StationBlockEntity blockEntity, IItemHandler items, ContainerData data) {
        this(containerId, inventory, blockEntity.getBlockPos(), blockEntity.station(), blockEntity, items, data);
    }

    private StationMenu(int containerId, Inventory inventory, BlockPos pos, Station station, StationBlockEntity blockEntity,
                        IItemHandler items, ContainerData data) {
        super(TechMenus.STATION.get(), containerId);
        this.station = station;
        this.pos = pos;
        this.data = data;
        this.blockEntity = blockEntity;
        this.recipes = TechRecipes.recipesFor(inventory.player.level(), station);

        for (int i = 0; i < StationBlockEntity.INPUT_SLOTS; i++) {
            addSlot(new SlotItemHandler(items, i, INPUT_X + (i % 3) * 18, INPUT_Y + (i / 3) * 18));
        }
        addSlot(new SlotItemHandler(items, StationBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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

    private static Station stationAt(Player player, BlockPos pos) {
        return player.level().getBlockState(pos).getBlock() instanceof StationBlock block ? block.station() : Station.ASSEMBLY_STATION;
    }

    public Station station() {
        return station;
    }

    public List<RecipeHolder<FabricationRecipe>> recipes() {
        return recipes;
    }

    public int selectedIndex() {
        return data.get(StationBlockEntity.DATA_SELECTED);
    }

    /** Production progress from 0 to 1. */
    public float progress() {
        int total = data.get(StationBlockEntity.DATA_TOTAL);
        return total <= 0 ? 0.0f : Math.min(1.0f, data.get(StationBlockEntity.DATA_PROGRESS) / (float) total);
    }

    /** Remaining production time in ticks. */
    public int remainingTicks() {
        return Math.max(0, data.get(StationBlockEntity.DATA_TOTAL) - data.get(StationBlockEntity.DATA_PROGRESS));
    }

    /** Button id = index in the recipe list. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null || id < 0 || id >= recipes.size()) {
            return false;
        }
        blockEntity.select(id);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof StationBlock
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
        int machineSlots = StationBlockEntity.INPUT_SLOTS + 1;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, StationBlockEntity.INPUT_SLOTS, false)) {
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
