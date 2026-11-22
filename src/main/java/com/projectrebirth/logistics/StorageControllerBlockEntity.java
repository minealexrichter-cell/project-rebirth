package com.projectrebirth.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.projectrebirth.config.RebirthConfig;
import com.projectrebirth.multiblock.MultiblockRegistry;
import com.projectrebirth.multiblock.MultiblockSchema;
import com.projectrebirth.multiblock.MultiblockTracker;
import com.projectrebirth.multiblock.PortBlockEntity;
import com.projectrebirth.multiblock.PortHost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 1.8.0, block J: the storage's controller and its store. The store is plain slots of a stack each (slots from the config; a
 * storage keeps its slots if the config later gives fewer), seen as one chest by the cargo ports, by the controller itself and by
 * work androids — while the construction is whole (looked at again when a block of it changes, as the other multiblocks). It
 * belongs to who set it up and their faction ({@link Storages}). It is meant as the base of the logistics of 1.11: who takes
 * what goes through {@link #portItems()}.
 */
public class StorageControllerBlockEntity extends BlockEntity implements MenuProvider, PortHost {
    private ItemStackHandler store;
    @Nullable
    private UUID owner;
    private boolean dirty = true;
    private boolean whole;
    @Nullable
    private MultiblockSchema.Check check;
    private final List<BlockPos> linked = new ArrayList<>();

    public StorageControllerBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsRegistry.STORAGE_ENTITY.get(), pos, state);
        store = newStore(slotsFromConfig());
    }

    private static int slotsFromConfig() {
        try {
            return RebirthConfig.STORAGE_SLOTS.get();
        } catch (IllegalStateException notLoaded) {
            return 256;
        }
    }

    private ItemStackHandler newStore(int slots) {
        return new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
    }

    // ------------------------------------------------------------------ who and where

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public Direction facing() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    /** Whether the construction is whole (the store can be used). */
    public boolean whole() {
        return whole && RebirthConfig.STORAGE_ENABLED.get();
    }

    @Nullable
    public MultiblockSchema.Check lastCheck() {
        return check;
    }

    /** The store itself, whole or not (the window and tests). */
    public ItemStackHandler store() {
        return store;
    }

    public int slots() {
        return store.getSlots();
    }

    /** Slots with something in them. */
    public int used() {
        int used = 0;
        for (int i = 0; i < store.getSlots(); i++) {
            if (!store.getStackInSlot(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    // ------------------------------------------------------------------ the ports

    @Override
    public boolean serves(BlockPos port) {
        return whole() && linked.contains(port);
    }

    @Override
    @Nullable
    public IEnergyStorage portEnergy() {
        return null;
    }

    @Override
    @Nullable
    public IItemHandler portItems() {
        return whole() ? store : null;
    }

    @Override
    public void markDirty() {
        dirty = true;
    }

    // ------------------------------------------------------------------ the construction

    void serverTick() {
        if (level == null || level.getGameTime() % 10 != 0) {
            return;
        }
        if (dirty) {
            revalidate();
        }
    }

    /** Looks at the construction now and links its ports. Public for tests. */
    @Nullable
    public MultiblockSchema.Check revalidate() {
        dirty = false;
        if (level == null) {
            return null;
        }
        MultiblockSchema schema = MultiblockRegistry.schema(level.registryAccess(), LogisticsRegistry.SCHEMA);
        boolean was = whole;
        if (schema == null || !schema.available()) {
            check = null;
            whole = false;
        } else {
            check = schema.check(level, worldPosition, facing(), owner);
            whole = check.complete();
            if (level instanceof ServerLevel server) {
                MultiblockTracker.track(server, worldPosition, check.bounds());
            }
        }
        List<BlockPos> ports = whole && check != null ? check.cargoPorts() : List.of();
        for (BlockPos old : linked) {
            if (!ports.contains(old) && level.getBlockEntity(old) instanceof PortBlockEntity port) {
                port.link(null);
            }
        }
        linked.clear();
        linked.addAll(ports);
        for (BlockPos pos : ports) {
            if (level.getBlockEntity(pos) instanceof PortBlockEntity port) {
                port.link(worldPosition);
            }
        }
        if (was != whole) {
            level.invalidateCapabilities(worldPosition);
            setChanged();
        }
        if (level instanceof ServerLevel server) {
            if (whole) {
                Storages.add(server, worldPosition);
            } else {
                Storages.remove(server, worldPosition);
            }
        }
        return check;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        dirty = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            Storages.remove(server, worldPosition);
            MultiblockTracker.untrack(server, worldPosition);
        }
    }

    /** The block is broken: everything in the store falls out. */
    void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < store.getSlots(); i++) {
            ItemStack stack = store.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack.copy());
                store.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        for (BlockPos old : linked) {
            if (level.getBlockEntity(old) instanceof PortBlockEntity port) {
                port.link(null);
            }
        }
        linked.clear();
    }

    // ------------------------------------------------------------------ the window

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.projectrebirth.storage_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new StorageMenu(containerId, inventory, this);
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("store", store.serializeNBT(registries));
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("store")) {
            CompoundTag saved = tag.getCompound("store");
            // a store built with more slots than the config gives now keeps them all
            int slots = Math.max(slotsFromConfig(), saved.getInt("Size"));
            store = newStore(slots);
            store.deserializeNBT(registries, saved);
            if (store.getSlots() < slots) {
                ItemStackHandler bigger = newStore(slots);
                for (int i = 0; i < store.getSlots(); i++) {
                    bigger.setStackInSlot(i, store.getStackInSlot(i));
                }
                store = bigger;
            }
        }
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }
}
