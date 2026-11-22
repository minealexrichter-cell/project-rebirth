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
public class StorageControllerBlockEntity extends BlockEntity implements MenuProvider, PortHost, com.projectrebirth.multiblock.SchemaHolder {
    private ItemStackHandler store;
    @Nullable
    private UUID owner;
    private boolean dirty = true;
    private boolean whole;
    @Nullable
    private MultiblockSchema.Check check;
    private final List<BlockPos> linked = new ArrayList<>();
    /** The hall over a whole storage: whether it stands (the client draws it), and the racks and frames hidden under it. */
    private boolean formed;
    private final List<BlockPos> hidden = new ArrayList<>();
    /** 1.8.0: what the racks show on their shelves (client: as last sent; four per rack), and whether the store changed since. */
    private List<ItemStack> shelves = List.of();
    private boolean shelvesChanged = true;
    private long shelvesSentAt;
    public static final int SHOWN_PER_RACK = 4;

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
                shelvesChanged = true;
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

    /** The plan of the storage (the hologram draws it). */
    @Override
    @Nullable
    public MultiblockSchema schema() {
        return level == null ? null : MultiblockRegistry.schema(level.registryAccess(), LogisticsRegistry.SCHEMA);
    }

    @Override
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
        // what lies on the shelves goes to the clients at most once a second, when the store changed
        if (shelvesChanged && formed && level.getGameTime() - shelvesSentAt >= 20) {
            shelvesChanged = false;
            shelvesSentAt = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Looks at the construction now and links its ports. Public for tests. */
    @Nullable
    public MultiblockSchema.Check revalidate() {
        dirty = false;
        if (level == null) {
            return null;
        }
        MultiblockSchema schema = schema();
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
        formShell(schema);
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

    /**
     * The hall (as the body workshop and the heavy dock): over a whole storage its racks and frames turn invisible and the clients
     * draw thin walls of corrugated steel round them (HallShell); the plates stay as the roof, the controller and the ports in front.
     */
    private void formShell(@Nullable MultiblockSchema schema) {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean now = whole && schema != null && schema.shell().isPresent();
        List<BlockPos> cells = new ArrayList<>();
        if (now) {
            String hide = schema.shell().get().hide();
            for (int y = 0; y < schema.height(); y++) {
                for (int r = 0; r < schema.depth(); r++) {
                    for (int c = 0; c < schema.width(); c++) {
                        if (hide.indexOf(schema.letter(y, r, c)) >= 0) {
                            cells.add(schema.worldPos(worldPosition, facing(), c, y, r).immutable());
                        }
                    }
                }
            }
        }
        for (BlockPos pos : hidden) {
            if (!cells.contains(pos)) {
                setFormed(pos, false);
            }
        }
        for (BlockPos pos : cells) {
            setFormed(pos, true);
        }
        hidden.clear();
        hidden.addAll(cells);
        if (formed != now) {
            formed = now;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    private void setFormed(BlockPos pos, boolean value) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof com.projectrebirth.multiblock.FrameBlock && state.getValue(com.projectrebirth.multiblock.FrameBlock.FORMED) != value) {
            level.setBlock(pos, state.setValue(com.projectrebirth.multiblock.FrameBlock.FORMED, value), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Whether the hall stands (the client draws it). */
    public boolean formed() {
        return formed;
    }

    /** Where the hall is drawn (round the controller, as far as the plan reaches). */
    public net.minecraft.world.phys.AABB renderBounds() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(5);
    }

    /** The number of racks of the plan (each looks after an equal share of the slots, in the order of the plan). */
    public static int racks(@Nullable MultiblockSchema schema) {
        int racks = 0;
        if (schema != null) {
            for (int y = 0; y < schema.height(); y++) {
                for (int r = 0; r < schema.depth(); r++) {
                    for (int c = 0; c < schema.width(); c++) {
                        if (schema.letter(y, r, c) == 'R') {
                            racks++;
                        }
                    }
                }
            }
        }
        return racks;
    }

    /** What each rack shows: up to four kinds of what lies in its share of the slots (one of each), empty stacks for room left. */
    private List<ItemStack> computeShelves() {
        int racks = racks(schema());
        List<ItemStack> out = new ArrayList<>();
        if (racks <= 0) {
            return out;
        }
        int per = Math.max(1, store.getSlots() / racks);
        for (int k = 0; k < racks; k++) {
            List<ItemStack> kinds = new ArrayList<>();
            for (int i = k * per; i < Math.min(store.getSlots(), (k + 1) * per) && kinds.size() < SHOWN_PER_RACK; i++) {
                ItemStack stack = store.getStackInSlot(i);
                if (!stack.isEmpty() && kinds.stream().noneMatch(kind -> ItemStack.isSameItemSameComponents(kind, stack))) {
                    kinds.add(stack.copyWithCount(1));
                }
            }
            while (kinds.size() < SHOWN_PER_RACK) {
                kinds.add(ItemStack.EMPTY);
            }
            out.addAll(kinds);
        }
        return out;
    }

    /** Client: what the racks show, four per rack in the order of the plan. */
    public List<ItemStack> shelves() {
        return shelves;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("formed", formed);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (ItemStack stack : computeShelves()) {
            list.add(stack.isEmpty() ? new CompoundTag() : stack.save(registries));
        }
        tag.put("shelves", list);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        formed = tag.getBoolean("formed");
        List<ItemStack> shown = new ArrayList<>();
        for (net.minecraft.nbt.Tag entry : tag.getList("shelves", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) entry;
            shown.add(item.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, item));
        }
        shelves = shown;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
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
        // the hall falls apart: the racks and frames show again
        for (BlockPos pos : hidden) {
            setFormed(pos, false);
        }
        hidden.clear();
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
        tag.putBoolean("formed", formed);
        tag.putLongArray("hidden", hidden.stream().mapToLong(BlockPos::asLong).toArray());
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
        formed = tag.getBoolean("formed");
        hidden.clear();
        for (long pos : tag.getLongArray("hidden")) {
            hidden.add(BlockPos.of(pos));
        }
    }
}
