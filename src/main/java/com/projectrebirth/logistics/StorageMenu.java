package com.projectrebirth.logistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 1.8.0, block J: the window of the storage. The store is shown through a view of 9 × 5 cells: the server fills them with what
 * matches the search (by name or id), sorted (by name, by count, by mod), from the row the window is scrolled to, then empty
 * slots to put things in. Shift-click puts a stack from the inventory anywhere into the store.
 */
public class StorageMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9;
    public static final int ROWS = 5;
    public static final int VIEW = COLUMNS * ROWS;
    public static final int VIEW_X = 8;
    public static final int VIEW_Y = 40;
    public static final int INVENTORY_X = 20;
    public static final int INVENTORY_Y = 164;
    public static final int DATA_SLOTS = 0, DATA_USED = 1, DATA_RESULTS = 2, DATA_WHOLE = 3, DATA_COUNT = 4;
    public static final int SORT_NAME = 0, SORT_COUNT = 1, SORT_MOD = 2;

    private final BlockPos pos;
    @Nullable
    private final StorageControllerBlockEntity storage;
    private final ContainerData data;
    /** Server: the store slot each cell of the view shows (-1: none). */
    private final int[] map = new int[VIEW];
    private String query = "";
    private int sort = SORT_NAME;
    private int row;

    /** On the client. */
    public StorageMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), null, new SimpleContainer(VIEW), new SimpleContainerData(DATA_COUNT));
    }

    /** On the server. */
    StorageMenu(int containerId, Inventory inventory, StorageControllerBlockEntity storage) {
        this(containerId, inventory, storage.getBlockPos(), storage, null, null);
    }

    private StorageMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable StorageControllerBlockEntity storage,
                        @Nullable Container client, @Nullable ContainerData clientData) {
        super(LogisticsRegistry.STORAGE_MENU.get(), containerId);
        this.pos = pos;
        this.storage = storage;
        java.util.Arrays.fill(map, -1);
        Container view = client != null ? client : new ViewContainer();
        for (int i = 0; i < VIEW; i++) {
            addSlot(new Slot(view, i, VIEW_X + (i % COLUMNS) * 18, VIEW_Y + (i / COLUMNS) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return StorageMenu.this.storage == null || StorageMenu.this.storage.whole();
                }

                @Override
                public boolean mayPickup(Player player) {
                    return StorageMenu.this.storage == null || StorageMenu.this.storage.whole();
                }
            });
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, c + r * 9 + 9, INVENTORY_X + c * 18, INVENTORY_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inventory, c, INVENTORY_X + c * 18, INVENTORY_Y + 58));
        }
        this.data = clientData != null ? clientData : new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_SLOTS -> StorageMenu.this.storage.slots();
                    case DATA_USED -> StorageMenu.this.storage.used();
                    case DATA_RESULTS -> results;
                    case DATA_WHOLE -> StorageMenu.this.storage.whole() ? 1 : 0;
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
        addDataSlots(data);
        if (storage != null) {
            rebuild();
        }
    }

    /** Matching stacks (server): how many, for the scroll bar. */
    private int results;

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    /** Server: the window asks for another view. */
    void view(String query, int sort, int row) {
        this.query = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        this.sort = sort;
        this.row = Math.max(0, row);
        rebuild();
        broadcastChanges();
    }

    /** Server: which store slot each cell shows — the matches, sorted, from the row on; then empty slots. */
    private void rebuild() {
        if (storage == null) {
            return;
        }
        ItemStackHandler store = storage.store();
        List<Integer> found = new ArrayList<>();
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < store.getSlots(); i++) {
            ItemStack stack = store.getStackInSlot(i);
            if (stack.isEmpty()) {
                empty.add(i);
            } else if (matches(stack)) {
                found.add(i);
            }
        }
        Comparator<Integer> order = switch (sort) {
            case SORT_COUNT -> Comparator.<Integer>comparingInt(i -> -store.getStackInSlot(i).getCount());
            case SORT_MOD -> Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(store.getStackInSlot(i).getItem()).toString());
            default -> Comparator.comparing(i -> store.getStackInSlot(i).getHoverName().getString());
        };
        found.sort(order.thenComparingInt(i -> i));
        results = found.size();
        List<Integer> shown = new ArrayList<>(found);
        if (query.isEmpty()) {
            shown.addAll(empty);
        } else if (!empty.isEmpty()) {
            // one empty slot after the matches, to put something in
            shown.add(empty.get(0));
        }
        int start = Math.min(row * COLUMNS, Math.max(0, (shown.size() - 1) / COLUMNS * COLUMNS));
        for (int i = 0; i < VIEW; i++) {
            int at = start + i;
            map[i] = at < shown.size() ? shown.get(at) : -1;
        }
    }

    private boolean matches(ItemStack stack) {
        if (query.isEmpty()) {
            return true;
        }
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(query);
    }

    @Override
    public void broadcastChanges() {
        if (storage != null) {
            rebuild();
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < VIEW) {
            if (!moveItemStackTo(stack, VIEW, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (storage == null || !storage.whole()) {
                return ItemStack.EMPTY;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(storage.store(), stack.copy(), false);
            if (rest.getCount() == stack.getCount()) {
                return ItemStack.EMPTY;
            }
            stack.setCount(rest.getCount());
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return storage == null || !storage.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    /** Server: the cells of the view, each a slot of the store (or nothing). */
    private final class ViewContainer implements Container {
        private ItemStackHandler store() {
            return storage.store();
        }

        @Override
        public int getContainerSize() {
            return VIEW;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public ItemStack getItem(int slot) {
            return map[slot] < 0 ? ItemStack.EMPTY : store().getStackInSlot(map[slot]);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return map[slot] < 0 ? ItemStack.EMPTY : store().extractItem(map[slot], amount, false);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (map[slot] < 0) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = store().getStackInSlot(map[slot]);
            store().setStackInSlot(map[slot], ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (map[slot] >= 0) {
                store().setStackInSlot(map[slot], stack);
            } else if (!stack.isEmpty()) {
                // a cell past the end of the view: anywhere in the store
                ItemStack rest = ItemHandlerHelper.insertItemStacked(store(), stack, false);
                if (!rest.isEmpty() && storage.getLevel() != null) {
                    net.minecraft.world.Containers.dropItemStack(storage.getLevel(), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, rest);
                }
            }
        }

        @Override
        public void setChanged() {
            storage.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
        }
    }
}
