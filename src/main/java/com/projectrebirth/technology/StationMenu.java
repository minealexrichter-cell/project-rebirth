package com.projectrebirth.technology;

import java.util.List;

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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Station window: recipe list (picked with a menu button click, like the stonecutter), six input slots, one output
 * slot and the player's inventory. Slot positions are shared with the screen.
 */
public class StationMenu extends AbstractContainerMenu {
    /** 1.8.0: the window of style A (docs/ui_mockups/01_station_A.html); inputs in two columns, 20 px apart. */
    public static final int INPUT_X = 159;
    public static final int INPUT_Y = 55;
    public static final int INPUT_COLUMNS = 2;
    public static final int INPUT_STEP = 20;
    public static final int OUTPUT_X = 355;
    public static final int OUTPUT_Y = 41;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 167;

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
                new ItemStackHandler(StationBlockEntity.INPUT_SLOTS + 1), new SimpleContainerData(StationBlockEntity.DATA_COUNT));
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
            addSlot(new SlotItemHandler(items, i, inputX(i), inputY(i)));
        }
        addSlot(new SlotItemHandler(items, StationBlockEntity.OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y) {
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

    public static int inputX(int slot) {
        return INPUT_X + (slot % INPUT_COLUMNS) * INPUT_STEP;
    }

    public static int inputY(int slot) {
        return INPUT_Y + (slot / INPUT_COLUMNS) * INPUT_STEP;
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

    /** The chosen recipe moved to a multiblock (1.7.3). */
    public boolean movedRecipe() {
        return data.get(StationBlockEntity.DATA_MOVED) != 0;
    }

    /** Production progress from 0 to 1. */
    public float progress() {
        int total = data.get(StationBlockEntity.DATA_TOTAL);
        return total <= 0 ? 0.0f : Math.min(1.0f, data.get(StationBlockEntity.DATA_PROGRESS) / (float) total);
    }

    /** Whether an electromagnetic storm holds the station back. */
    public boolean emiPaused() {
        return data.get(StationBlockEntity.DATA_EMI) != 0;
    }

    public int energy() {
        return EnergyUtil.join(data.get(StationBlockEntity.DATA_ENERGY_LOW), data.get(StationBlockEntity.DATA_ENERGY_HIGH));
    }

    public int energyCapacity() {
        return EnergyUtil.join(data.get(StationBlockEntity.DATA_CAPACITY_LOW), data.get(StationBlockEntity.DATA_CAPACITY_HIGH));
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
